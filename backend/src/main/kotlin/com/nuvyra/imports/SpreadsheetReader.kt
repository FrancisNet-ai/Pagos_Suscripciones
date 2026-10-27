package com.nuvyra.imports

import com.nuvyra.common.error.ApiException
import org.apache.poi.openxml4j.util.ZipSecureFile
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.text.Normalizer

/** Una fila leída: número de fila en el archivo (para mensajes) y celdas por columna reconocida. */
data class RawRow(val rowNumber: Int, val cells: Map<ImportColumn, String>)

enum class ImportColumn(vararg val aliases: String) {
    NAME("nombre", "nombrecompleto", "cliente", "nombres"),
    PHONE("celular", "telefono", "whatsapp", "numero", "movil"),
    NOTES("notas", "nota", "observaciones", "comentarios"),
    PRODUCT("producto", "servicio", "plan"),
    START("inicio", "fechainicio", "fechadeinicio", "desde"),
    DURATION("duraciondias", "duracion", "dias"),
    END("vencimiento", "fechavencimiento", "fechadevencimiento", "hasta", "vence"),
    PRICE("precio", "importe", "monto", "pago"),
    STATUS("estado"),
    ;

    companion object {
        fun of(header: String): ImportColumn? {
            val key = normalize(header)
            return entries.firstOrNull { key in it.aliases }
        }

        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase().replace(Regex("[^a-z0-9]"), "")
    }
}

/**
 * Lee CSV (coma o punto y coma, UTF-8 o Windows-1252) y Excel `.xlsx`. Solo la primera hoja.
 * La primera fila debe tener los encabezados; las columnas desconocidas se ignoran.
 */
object SpreadsheetReader {
    const val MAX_ROWS = 2000

    fun read(fileName: String, bytes: ByteArray): List<RawRow> {
        if (bytes.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "El archivo está vacío.")
        val rows = when {
            isZip(bytes) -> readXlsx(bytes)
            isOle2(bytes) -> throw ApiException.badRequest("UNSUPPORTED_FILE", "Los archivos .xls antiguos no se admiten. Guárdalo como .xlsx o .csv.")
            fileName.lowercase().endsWith(".xlsx") -> throw ApiException.badRequest("UNSUPPORTED_FILE", "El archivo no es un Excel válido.")
            else -> readCsv(decode(bytes))
        }
        if (rows.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "El archivo no tiene encabezados.")
        val header = rows.first().map { ImportColumn.of(it) }
        if (ImportColumn.NAME !in header || ImportColumn.PHONE !in header) {
            throw ApiException.badRequest("MISSING_COLUMNS", "El archivo debe tener al menos las columnas «nombre» y «celular».")
        }
        val data = rows.drop(1).withIndex().filter { (_, cells) -> cells.any { it.isNotBlank() } }
        if (data.isEmpty()) throw ApiException.badRequest("EMPTY_FILE", "El archivo no tiene filas con datos.")
        if (data.size > MAX_ROWS) throw ApiException.badRequest("TOO_MANY_ROWS", "El archivo tiene más de $MAX_ROWS filas. Divídelo en partes.")
        return data.map { (index, cells) ->
            RawRow(
                rowNumber = index + 2,
                cells = header.withIndex().mapNotNull { (i, column) -> column?.let { it to cells.getOrElse(i) { "" }.trim() } }
                    .filter { it.second.isNotEmpty() }.toMap(),
            )
        }
    }

    private fun isZip(b: ByteArray) = b.size > 4 && b[0] == 0x50.toByte() && b[1] == 0x4B.toByte()
    private fun isOle2(b: ByteArray) = b.size > 8 && b[0] == 0xD0.toByte() && b[1] == 0xCF.toByte()

    private fun readXlsx(bytes: ByteArray): List<List<String>> {
        // Protección ante archivos comprimidos maliciosos (zip bomb).
        ZipSecureFile.setMinInflateRatio(0.01)
        ZipSecureFile.setMaxEntrySize(50L * 1024 * 1024)
        val workbook = try {
            XSSFWorkbook(ByteArrayInputStream(bytes))
        } catch (ex: Exception) {
            throw ApiException.badRequest("UNSUPPORTED_FILE", "No pudimos leer el Excel. Guárdalo de nuevo como .xlsx o .csv.")
        }
        return workbook.use { wb ->
            val sheet = wb.getSheetAt(0)
            val formatter = DataFormatter(java.util.Locale.forLanguageTag("es-PE"))
            if (sheet.lastRowNum > MAX_ROWS + 1) throw ApiException.badRequest("TOO_MANY_ROWS", "El archivo tiene más de $MAX_ROWS filas. Divídelo en partes.")
            (0..sheet.lastRowNum).map { r ->
                val row = sheet.getRow(r) ?: return@map emptyList()
                (0 until maxOf(row.lastCellNum.toInt(), 0)).map { c ->
                    val cell = row.getCell(c) ?: return@map ""
                    if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                        cell.localDateTimeCellValue.toLocalDate().toString()
                    } else {
                        formatter.formatCellValue(cell)
                    }
                }
            }
        }
    }

    private fun decode(bytes: ByteArray): String {
        val withoutBom = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) bytes.copyOfRange(3, bytes.size) else bytes
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(withoutBom)).toString()
        } catch (ex: CharacterCodingException) {
            // Excel en Windows guarda CSV en Windows-1252.
            String(withoutBom, Charset.forName("windows-1252"))
        }
    }

    /** CSV con comillas dobles; el separador (coma o punto y coma) se deduce de la primera línea. */
    fun readCsv(text: String): List<List<String>> {
        val firstLine = text.lineSequence().firstOrNull().orEmpty()
        val separator = if (firstLine.count { it == ';' } > firstLine.count { it == ',' }) ';' else ','
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                !quoted && ch == separator -> { row.add(cell.toString()); cell.clear() }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row.add(cell.toString()); cell.clear()
                    rows.add(row); row = mutableListOf()
                    if (rows.size > MAX_ROWS + 1) throw ApiException.badRequest("TOO_MANY_ROWS", "El archivo tiene más de $MAX_ROWS filas. Divídelo en partes.")
                }
                else -> cell.append(ch)
            }
            i++
        }
        if (cell.isNotEmpty() || row.isNotEmpty()) {
            row.add(cell.toString())
            rows.add(row)
        }
        return rows
    }
}
