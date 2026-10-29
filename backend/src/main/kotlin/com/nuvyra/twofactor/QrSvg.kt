package com.nuvyra.twofactor

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.util.Base64

/** Código QR como SVG en una URL `data:` (la CSP ya permite `img-src data:`); no usa AWT. */
object QrSvg {
    fun dataUrl(content: String): String {
        val matrix = QRCodeWriter().encode(
            content, BarcodeFormat.QR_CODE, 0, 0,
            mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 4),
        )
        val path = buildString {
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) if (matrix[x, y]) append("M${x},${y}h1v1h-1z")
        }
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${matrix.width} ${matrix.height}" shape-rendering="crispEdges">""" +
            """<rect width="100%" height="100%" fill="#fff"/><path fill="#000" d="$path"/></svg>"""
        return "data:image/svg+xml;base64," + Base64.getEncoder().encodeToString(svg.toByteArray())
    }
}
