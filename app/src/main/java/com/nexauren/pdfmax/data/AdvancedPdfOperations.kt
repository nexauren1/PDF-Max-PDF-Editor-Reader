package com.nexauren.pdfmax.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Extra local PDF/Office operations shared by the tool workspaces. */
object AdvancedPdfOperations {
    private fun init(context: Context) = PDFBoxResourceLoader.init(context.applicationContext)

    private fun target(input: File, suffix: String, extension: String = "pdf"): File {
        val safe = input.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(PdfOperations.outputDirectory(), safe + "_" + suffix + "_" + System.currentTimeMillis() + "." + extension)
    }

    /** Rebuilds a PDF in the exact page order supplied by the user (1-based indices). */
    fun reorderPages(context: Context, input: File, pageOrder: List<Int>): File {
        init(context)
        require(pageOrder.isNotEmpty()) { "Indique pelo menos uma página." }
        require(pageOrder.distinct().size == pageOrder.size) { "A ordem não pode repetir páginas." }
        val destination = target(input, "organized")
        PDDocument.load(input).use { source ->
            require(pageOrder.all { it in 1..source.numberOfPages }) { "A ordem contém uma página inexistente." }
            PDDocument().use { output ->
                pageOrder.forEach { pageNumber -> output.importPage(source.getPage(pageNumber - 1)) }
                output.save(destination)
            }
        }
        return destination
    }

    /** Creates a PDF without the selected 1-based page numbers. */
    fun removePages(context: Context, input: File, pagesToRemove: Set<Int>): File {
        init(context)
        val destination = target(input, "pages_removed")
        PDDocument.load(input).use { source ->
            require(pagesToRemove.isNotEmpty()) { "Selecione pelo menos uma página." }
            require(pagesToRemove.all { it in 1..source.numberOfPages }) { "A seleção contém uma página inexistente." }
            require(pagesToRemove.size < source.numberOfPages) { "Não pode eliminar todas as páginas." }
            PDDocument().use { output ->
                for (index in 1..source.numberOfPages) {
                    if (index !in pagesToRemove) output.importPage(source.getPage(index - 1))
                }
                output.save(destination)
            }
        }
        return destination
    }

    /** Fills an existing AcroForm text field in-place in a new copy. */
    fun fillFormField(context: Context, input: File, fieldName: String, value: String): File {
        init(context)
        require(fieldName.isNotBlank()) { "Indique o nome do campo do formulário." }
        val destination = target(input, "form_filled")
        PDDocument.load(input).use { document ->
            val form = document.documentCatalog.acroForm
                ?: error("Este PDF não contém um formulário AcroForm editável.")
            val field = form.getField(fieldName.trim())
                ?: error("O campo '" + fieldName + "' não existe neste formulário.")
            field.setValue(value)
            document.save(destination)
        }
        return destination
    }

    fun formFieldNames(context: Context, input: File): List<String> {
        init(context)
        return PDDocument.load(input).use { document ->
            val form = document.documentCatalog.acroForm ?: return@use emptyList()
            form.fields.mapNotNull { it.fullyQualifiedName }.filter { it.isNotBlank() }.sorted()
        }
    }

    /**
     * Places a handwritten signature image on the last page.
     * Coordinates are normalized to 0..1. This is a visual e-signature,
     * not a certificate-backed cryptographic digital signature.
     */
    fun addHandwrittenSignature(context: Context, input: File, strokes: List<List<Pair<Float, Float>>>): File {
        init(context)
        require(strokes.any { it.size >= 2 }) { "Desenhe a assinatura antes de continuar." }
        val bitmap = Bitmap.createBitmap(900, 250, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.rgb(26, 76, 160)
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            strokes.forEach { stroke ->
                if (stroke.size >= 2) {
                    val path = Path()
                    stroke.forEachIndexed { index, point ->
                        val x = point.first.coerceIn(0f, 1f) * 900f
                        val y = point.second.coerceIn(0f, 1f) * 250f
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    canvas.drawPath(path, paint)
                }
            }
            val destination = target(input, "signed")
            PDDocument.load(input).use { document ->
                require(document.numberOfPages > 0) { "O PDF não contém páginas." }
                val page = document.getPage(document.numberOfPages - 1)
                val image = LosslessFactory.createFromImage(document, bitmap)
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.drawImage(image, 42f, 34f, 210f, 58f)
                }
                document.save(destination)
            }
            return destination
        } finally {
            bitmap.recycle()
        }
    }

    /** Converts supported OOXML/text Office documents to text-based PDF locally. */
    fun officeToPdf(context: Context, input: File): File {
        val supported = setOf("docx", "xlsx", "pptx", "txt", "csv", "rtf", "md", "log")
        require(input.extension.lowercase(Locale.ROOT) in supported) {
            "Este formato Office antigo não pode ser convertido localmente. Guarde-o como DOCX, XLSX ou PPTX."
        }
        val text = OfficeTextExtractor.extract(input)
        require(text.isNotBlank()) { "Não foi possível extrair texto deste documento." }
        val created = PdfOperations.createPdfFromText(context, input.nameWithoutExtension, text)
        val destination = target(input, "converted", "pdf")
        if (!created.copyTo(destination, overwrite = true).exists()) error("Não foi possível guardar o PDF convertido.")
        created.delete()
        return destination
    }

    /** Produces a valid .xlsx workbook with extracted PDF text in the first column. */
    fun pdfToExcel(context: Context, input: File): File {
        val text = PdfOperations.extractText(context, input)
        require(text.isNotBlank()) { "Não foi encontrado texto selecionável no PDF." }
        val lines = text.replace("\r", "").lines().ifEmpty { listOf(text) }
        val destination = target(input, "converted", "xlsx")
        writeXlsx(destination, lines)
        return destination
    }

    /** Combines multiple images into a multi-page A4 PDF. */
    fun imagesToPdf(context: Context, images: List<File>): File {
        init(context)
        require(images.isNotEmpty()) { "Selecione pelo menos uma imagem." }
        val destination = File(PdfOperations.outputDirectory(), "PDF_Max_Scan_" + System.currentTimeMillis() + ".pdf")
        PDDocument().use { document ->
            images.forEach { imageFile ->
                val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                    ?: error("Não foi possível abrir " + imageFile.name + ".")
                try {
                    val page = com.tom_roush.pdfbox.pdmodel.PDPage(com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4)
                    document.addPage(page)
                    val image = LosslessFactory.createFromImage(document, bitmap)
                    val pageWidth = page.mediaBox.width
                    val pageHeight = page.mediaBox.height
                    val scale = minOf((pageWidth - 48f) / bitmap.width, (pageHeight - 48f) / bitmap.height)
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    PDPageContentStream(document, page).use {
                        it.drawImage(image, (pageWidth - width) / 2f, (pageHeight - height) / 2f, width, height)
                    }
                } finally {
                    bitmap.recycle()
                }
            }
            document.save(destination)
        }
        return destination
    }

    /** Inserts a blank A4 page after the requested page (0 means before page 1). */
    fun insertBlankPage(context: Context, input: File, pageAfter: Int): File {
        init(context)
        val destination = target(input, "blank_page_added")
        PDDocument.load(input).use { source ->
            require(pageAfter in 0..source.numberOfPages) {
                "A posição deve estar entre 0 e " + source.numberOfPages + "."
            }
            PDDocument().use { output ->
                for (index in 0..source.numberOfPages) {
                    if (index == pageAfter) {
                        output.addPage(com.tom_roush.pdfbox.pdmodel.PDPage(
                            com.tom_roush.pdfbox.pdmodel.common.PDRectangle.A4
                        ))
                    }
                    if (index < source.numberOfPages) {
                        output.importPage(source.getPage(index))
                    }
                }
                output.save(destination)
            }
        }
        return destination
    }

    /** Creates a separate PDF from the selected pages, preserving the entered order. */
    fun extractPages(context: Context, input: File, pageNumbers: List<Int>): File {
        return reorderPages(context, input, pageNumbers)
    }

    /** Updates common document metadata without changing the PDF page content. */
    fun editMetadata(
        context: Context,
        input: File,
        title: String,
        author: String,
        subject: String,
        keywords: String
    ): File {
        init(context)
        val destination = target(input, "metadata")
        PDDocument.load(input).use { document ->
            document.documentInformation.title = title.trim()
            document.documentInformation.author = author.trim()
            document.documentInformation.subject = subject.trim()
            document.documentInformation.keywords = keywords.trim()
            document.save(destination)
        }
        return destination
    }

    private fun writeXlsx(target: File, lines: List<String>) {
        val rows = lines.mapIndexed { index, line ->
            val value = xmlEscape(line.take(32000))
            "<row r=\"" + (index + 1) + "\"><c r=\"A" + (index + 1) + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + value + "</t></is></c></row>"
        }.joinToString("")
        val entries = linkedMapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="PDF Max" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>""",
            "xl/worksheets/sheet1.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>"""
        )
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            entries.forEach { (name, value) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
