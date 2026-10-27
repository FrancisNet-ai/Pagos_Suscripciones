package com.platform.imports

import com.platform.support.IntegrationTest
import com.platform.support.StoreFixture
import jakarta.servlet.http.Cookie
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

class CustomerImportIntegrationTest : IntegrationTest() {
    @Autowired lateinit var jdbc: JdbcTemplate
    lateinit var fx: StoreFixture

    @BeforeEach
    fun setUp() {
        fx = StoreFixture(mockMvc)
    }

    private fun upload(seller: Cookie, name: String, bytes: ByteArray): ResultActions =
        mockMvc.perform(multipart("/api/v1/customers/imports").file(MockMultipartFile("file", name, "application/octet-stream", bytes)).with(csrf()).cookie(seller))

    private fun dmy(date: LocalDate) = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    @Test
    fun `vista previa con errores por fila y confirmacion que crea clientes y suscripciones`() {
        val email = uniqueEmail()
        val seller = registerSeller(email)
        val store = fx.create(seller, capacity = 5)
        fx.order(store.slug, store.productId, phone = "911222000", name = "Ya Existe").andExpect(status().isCreated)
        val today = LocalDate.now()
        // Separador «;» y encabezados con tildes, como guarda Excel en español.
        val csv = """
            Nombre;Celular;Producto;Inicio;Duración días;Precio;Notas
            Ana Torres;986 111 222;servicio premium;${dmy(today.minusDays(10))};30;S/ 12,50;perfil 2
            Luis Díaz;986111333;;;;;sin suscripción
            Ya Existe;911222000;Servicio Premium;${dmy(today.minusDays(40))};30;15;
            Sin Celular;12345;;;;;
            Repetida;986-111-222;;;;;
            Otro Producto;986111444;Curso de cocina;${dmy(today)};30;20;
        """.trimIndent()
        val preview = upload(seller, "clientes.csv", csv.toByteArray()).andExpect(status().isCreated)
            .andExpect(jsonPath("$.totalRows").value(6))
            .andExpect(jsonPath("$.summary.toCreate").value(2))
            .andExpect(jsonPath("$.summary.existing").value(1))
            .andExpect(jsonPath("$.summary.withSubscription").value(2))
            .andExpect(jsonPath("$.summary.errors").value(3))
            .andExpect(jsonPath("$.rows[0].phone").value("+51986111222"))
            .andExpect(jsonPath("$.rows[0].price").value(12.5))
            .andExpect(jsonPath("$.rows[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.rows[2].status").value("EXPIRED"))
            .andExpect(jsonPath("$.rows[3].errors[0]").value("Celular no válido: «12345»"))
            .andExpect(jsonPath("$.rows[4].errors[0]").value("Celular repetido en el archivo (fila 2)"))
            .andExpect(jsonPath("$.rows[5].errors[0]").value("No existe el producto «Curso de cocina». Créalo primero o deja la columna vacía."))
            .andReturn().response.contentAsString
        val importId = fx.idFrom(preview)
        val businessId = businessIdOf(email)
        // La vista previa no crea nada.
        assertThat(jdbc.queryForObject("select count(*) from customers where business_id = ? and source = 'IMPORT'", Int::class.java, businessId)).isZero()

        mockMvc.perform(post("/api/v1/customers/imports/$importId/commit").with(csrf()).cookie(seller)).andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("COMMITTED"))
            .andExpect(jsonPath("$.result.created").value(2))
            .andExpect(jsonPath("$.result.subscriptionsAdded").value(2))
            .andExpect(jsonPath("$.result.skipped").value(3))
        assertThat(jdbc.queryForObject("select count(*) from customers where business_id = ? and source = 'IMPORT'", Int::class.java, businessId)).isEqualTo(2)
        // La suscripción activa ocupa un cupo; la vencida del cliente existente no.
        assertThat(jdbc.queryForObject("select count(*) from capacity_allocations where product_id = ?::uuid and allocation_status = 'OCCUPIED'", Int::class.java, store.productId)).isEqualTo(1)
        // Lo importado no se cuenta como venta.
        assertThat(jdbc.queryForObject("select count(*) from orders where business_id = ? and status = 'PAID'", Int::class.java, businessId)).isZero()

        mockMvc.perform(post("/api/v1/customers/imports/$importId/commit").with(csrf()).cookie(seller))
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("IMPORT_CLOSED"))
        mockMvc.perform(get("/api/v1/customers/imports/$importId").cookie(registerSeller())).andExpect(status().isNotFound)
    }

    @Test
    fun `lee Excel con fechas y celulares numericos`() {
        val seller = registerSeller()
        fx.create(seller)
        val out = ByteArrayOutputStream()
        XSSFWorkbook().use { wb ->
            val sheet = wb.createSheet("Clientes")
            val dateStyle = wb.createCellStyle().apply { dataFormat = wb.creationHelper.createDataFormat().getFormat("dd/mm/yyyy") }
            sheet.createRow(0).apply { listOf("nombre", "celular", "producto", "fecha de inicio", "vencimiento").forEachIndexed { i, h -> createCell(i).setCellValue(h) } }
            sheet.createRow(1).apply {
                createCell(0).setCellValue("Carmen Ruiz")
                createCell(1).setCellValue(987654000.0)
                createCell(2).setCellValue("Servicio Premium")
                createCell(3).apply { setCellValue(LocalDate.now().minusDays(5)); cellStyle = dateStyle }
                createCell(4).apply { setCellValue(LocalDate.now().plusDays(25)); cellStyle = dateStyle }
            }
            wb.write(out)
        }
        upload(seller, "clientes.xlsx", out.toByteArray()).andExpect(status().isCreated)
            .andExpect(jsonPath("$.summary.toCreate").value(1))
            .andExpect(jsonPath("$.rows[0].phone").value("+51987654000"))
            .andExpect(jsonPath("$.rows[0].durationDays").value(30))
            .andExpect(jsonPath("$.rows[0].status").value("ACTIVE"))
    }

    @Test
    fun `respeta el limite de clientes del plan y rechaza archivos sin columnas obligatorias`() {
        val email = uniqueEmail()
        val seller = registerSeller(email, withPlan = false)
        jdbc.update("insert into saas_plans (name, price, billing_period, customer_limit) values ('Dos clientes', 10, 'MONTHLY', 2) on conflict (name) do nothing")
        grantPlan(businessIdOf(email), jdbc.queryForObject("select id from saas_plans where name = 'Dos clientes'", UUID::class.java)!!)
        val csv = "nombre,celular\nUno,987000001\nDos,987000002\nTres,987000003\n"
        upload(seller, "c.csv", csv.toByteArray()).andExpect(status().isCreated)
            .andExpect(jsonPath("$.summary.toCreate").value(2))
            .andExpect(jsonPath("$.rows[2].errors[0]").value("Supera el límite de clientes de tu plan"))

        upload(seller, "c.csv", "nombre,correo\nUno,a@b.pe\n".toByteArray())
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("MISSING_COLUMNS"))
        upload(seller, "c.xls", byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0, 0, 0, 0, 0, 0))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE"))
    }

    @Test
    fun `sin plan vigente no se puede importar`() {
        val seller = registerSeller(withPlan = false)
        upload(seller, "c.csv", "nombre,celular\nUno,987000001\n".toByteArray())
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("PLAN_REQUIRED"))
    }
}
