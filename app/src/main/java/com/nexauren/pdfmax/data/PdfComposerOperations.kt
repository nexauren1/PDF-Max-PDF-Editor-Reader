package com.nexauren.pdfmax.data

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.File
import kotlin.math.max

/** Creates styled, multi-page PDF documents with headings, typography and a real table. */
object PdfComposerOperations {
    fun createDocument(context: Context, title: String, subtitle: String, body: String,
        fontFamily: String, bodyStyle: String, bodyFontSize: Int, tableRows: List<List<String>>): File {
        val cleanTitle = title.trim().ifBlank { "PDF Max Document" }
        require(body.isNotBlank() || tableRows.any { row -> row.any { it.isNotBlank() } }) {
            "Add some text or a table before creating the PDF."
        }
        PDFBoxResourceLoader.init(context.applicationContext)
        val directory = PdfOperations.outputDirectory()
        require(directory.exists() || directory.mkdirs()) { "The output folder could not be created." }
        require(directory.canWrite()) { "PDF Max cannot write to Documents/PDF Max. Check storage access." }
        val safeName = cleanTitle.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').take(60).ifBlank { "PDF_Max" }
        val target = File(directory, safeName + "_" + System.currentTimeMillis() + ".pdf")
        val regularFont = font(fontFamily, false, false)
        val titleFont = font(fontFamily, true, false)
        val bodyFont = font(fontFamily, bodyStyle == "Bold", bodyStyle == "Italic")
        val margin = 46f
        val bottom = 52f
        val width = PDRectangle.A4.width - margin * 2f

        PDDocument().use { doc ->
            var page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            var stream = PDPageContentStream(doc, page)
            var y = page.mediaBox.height - 58f

            fun newPage() {
                stream.close()
                page = PDPage(PDRectangle.A4)
                doc.addPage(page)
                stream = PDPageContentStream(doc, page)
                y = page.mediaBox.height - 56f
            }
            fun draw(value: String, typeface: PDFont, size: Float, x: Float, baseline: Float,
                red: Int = 40, green: Int = 52, blue: Int = 68) {
                val safe = safeText(value)
                if (safe.isEmpty()) return
                stream.beginText()
                stream.setFont(typeface, size)
                stream.setNonStrokingColor(red, green, blue)
                stream.newLineAtOffset(x, baseline)
                stream.showText(safe)
                stream.endText()
            }
            fun wrap(value: String, typeface: PDFont, size: Float, maxWidth: Float): List<String> {
                val words = safeText(value).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (words.isEmpty()) return listOf("")
                val result = mutableListOf<String>()
                var line = ""
                for (word in words) {
                    val candidate = if (line.isEmpty()) word else "$line $word"
                    val measured = runCatching { typeface.getStringWidth(candidate) * size / 1000f }.getOrDefault(maxWidth + 1f)
                    if (measured <= maxWidth) line = candidate
                    else {
                        if (line.isNotEmpty()) result.add(line)
                        line = ""
                        var part = ""
                        for (char in word) {
                            val next = part + char
                            val nextWidth = runCatching { typeface.getStringWidth(next) * size / 1000f }.getOrDefault(maxWidth + 1f)
                            if (nextWidth > maxWidth && part.isNotEmpty()) { result.add(part); part = char.toString() }
                            else part = next
                        }
                        line = part
                    }
                }
                if (line.isNotEmpty()) result.add(line)
                return result.ifEmpty { listOf("") }
            }
            fun paragraph(value: String, typeface: PDFont, size: Float, leading: Float, gap: Float) {
                value.replace("\r", "").split("\n").forEach { part ->
                    if (part.isBlank()) { y -= leading * 0.65f }
                    else wrap(part, typeface, size, width).forEach { line ->
                        if (y < bottom + leading) newPage()
                        draw(line, typeface, size, margin, y)
                        y -= leading
                    }
                }
                y -= gap
            }

            paragraph(cleanTitle, titleFont, 25f, 31f, 5f)
            if (subtitle.isNotBlank()) paragraph(subtitle.trim(), regularFont, 13f, 18f, 11f)
            stream.setStrokingColor(45, 132, 235)
            stream.setLineWidth(1.2f)
            stream.moveTo(margin, y)
            stream.lineTo(PDRectangle.A4.width - margin, y)
            stream.stroke()
            y -= 22f
            if (body.isNotBlank()) paragraph(body, bodyFont, bodyFontSize.coerceIn(9, 16).toFloat(),
                bodyFontSize.coerceIn(9, 16) * 1.55f, 16f)

            val rows = tableRows.map { row -> (0 until 3).map { col -> row.getOrNull(col).orEmpty().trim() } }
                .filter { row -> row.any { it.isNotBlank() } }
            val cellWidth = width / 3f
            rows.forEachIndexed { index, row ->
                val isHeader = index == 0
                val cellFont = if (isHeader) titleFont else regularFont
                val size = if (isHeader) 9.5f else 9f
                val lines = row.map { wrap(it, cellFont, size, cellWidth - 12f) }
                val rowHeight = max(27f, (lines.maxOfOrNull { it.size } ?: 1) * 12f + 12f)
                if (y - rowHeight < bottom) newPage()
                val top = y
                for (col in 0 until 3) {
                    val x = margin + col * cellWidth
                    if (isHeader) {
                        stream.setNonStrokingColor(225, 237, 252)
                        stream.addRect(x, top - rowHeight, cellWidth, rowHeight)
                        stream.fill()
                    }
                    stream.setStrokingColor(185, 202, 224)
                    stream.setLineWidth(0.55f)
                    stream.addRect(x, top - rowHeight, cellWidth, rowHeight)
                    stream.stroke()
                    lines[col].forEachIndexed { lineIndex, line ->
                        draw(line, cellFont, size, x + 6f, top - 15f - lineIndex * 12f,
                            if (isHeader) 29 else 49, if (isHeader) 66 else 61, if (isHeader) 102 else 75)
                    }
                }
                y = top - rowHeight
            }

            stream.close()
            val total = doc.numberOfPages
            for (i in 0 until total) {
                val footer = PDPageContentStream(doc, doc.getPage(i), PDPageContentStream.AppendMode.APPEND, true, true)
                footer.beginText()
                footer.setFont(PDType1Font.HELVETICA, 8f)
                footer.setNonStrokingColor(115, 132, 154)
                val label = safeText("PDF Max | " + (i + 1) + " / " + total)
                val labelWidth = PDType1Font.HELVETICA.getStringWidth(label) * 8f / 1000f
                footer.newLineAtOffset(PDRectangle.A4.width - margin - labelWidth, 25f)
                footer.showText(label)
                footer.endText()
                footer.close()
            }
            doc.documentInformation.title = cleanTitle
            doc.documentInformation.author = "PDF Max"
            doc.documentInformation.subject = "Created with PDF Max"
            doc.save(target)
        }
        return target
    }

    private fun font(family: String, bold: Boolean, italic: Boolean): PDFont = when (family) {
        "Serif" -> when {
            bold && italic -> PDType1Font.TIMES_BOLD_ITALIC
            bold -> PDType1Font.TIMES_BOLD
            italic -> PDType1Font.TIMES_ITALIC
            else -> PDType1Font.TIMES_ROMAN
        }
        "Mono" -> when {
            bold && italic -> PDType1Font.COURIER_BOLD_OBLIQUE
            bold -> PDType1Font.COURIER_BOLD
            italic -> PDType1Font.COURIER_OBLIQUE
            else -> PDType1Font.COURIER
        }
        else -> when {
            bold && italic -> PDType1Font.HELVETICA_BOLD_OBLIQUE
            bold -> PDType1Font.HELVETICA_BOLD
            italic -> PDType1Font.HELVETICA_OBLIQUE
            else -> PDType1Font.HELVETICA
        }
    }
    private fun safeText(value: String): String {
        val extra = "€‚ƒ„…†‡ˆ‰Š‹ŒŽ‘’“”•–—˜™š›œžŸ"
        return value.map { c -> if (c.code in 32..126 || c.code in 160..255 || c in extra || c == '\t') c else ' ' }.joinToString("")
    }
}
