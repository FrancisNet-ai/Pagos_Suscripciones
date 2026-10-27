package com.nuvyra.imports

import com.nuvyra.subscriptions.ManualStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class SpreadsheetReaderTest {
    @Test
    fun `CSV con comillas, separador punto y coma y Windows-1252`() {
        val text = "Nombre;Teléfono;Notas\r\n\"Pérez, Ana\";987111222;\"dice \"\"hola\"\"\"\r\n;;\r\n"
        val rows = SpreadsheetReader.read("c.csv", text.toByteArray(charset("windows-1252")))
        assertThat(rows).hasSize(1)
        assertThat(rows[0].rowNumber).isEqualTo(2)
        assertThat(rows[0].cells).containsEntry(ImportColumn.NAME, "Pérez, Ana").containsEntry(ImportColumn.PHONE, "987111222")
            .containsEntry(ImportColumn.NOTES, "dice \"hola\"")
    }

    @Test
    fun `fechas, precios y estados en formatos habituales`() {
        assertThat(CustomerImportService.parseDate("5/3/2026")).isEqualTo(LocalDate.of(2026, 3, 5))
        assertThat(CustomerImportService.parseDate("2026-03-05")).isEqualTo(LocalDate.of(2026, 3, 5))
        assertThat(CustomerImportService.parseDate("05/03/26")).isEqualTo(LocalDate.of(2026, 3, 5))
        assertThat(CustomerImportService.parseDate("marzo")).isNull()
        assertThat(CustomerImportService.parsePrice("S/ 12,50")).isEqualByComparingTo(BigDecimal("12.50"))
        assertThat(CustomerImportService.parsePrice("1,250.00")).isEqualByComparingTo(BigDecimal("1250.00"))
        assertThat(CustomerImportService.parseStatus("Vigente")).isEqualTo(ManualStatus.ACTIVE)
        assertThat(CustomerImportService.parseStatus("VENCIDA")).isEqualTo(ManualStatus.EXPIRED)
    }
}
