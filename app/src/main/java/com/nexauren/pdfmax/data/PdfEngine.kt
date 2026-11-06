package com.nexauren.pdfmax.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.rendering.PDFRenderer
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.min

object PdfEngine {
    fun createPdfFromImages(context: Context, imageUris: List<Uri>, output: File) {
        require(imageUris.isNotEmpty()) { "Select at least one image" }
        output.parentFile?.mkdirs()
        val document = PdfDocument()
        try {
            imageUris.forEachIndexed { index, uri ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, bounds)
                }
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Image ${index + 1} could not be decoded" }
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, 1800, 2400)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, options)
                } ?: error("Unable to read image ${index + 1}")

                try {
                    val pageInfo = PdfDocument.PageInfo.Builder(595, 842, index + 1).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawColor(android.graphics.Color.WHITE)
                    val scale = min(555f / bitmap.width.toFloat(), 802f / bitmap.height.toFloat())
                    val width = bitmap.width * scale
                    val height = bitmap.height * scale
                    val destination = RectF(
                        (595f - width) / 2f,
                        (842f - height) / 2f,
                        (595f + width) / 2f,
                        (842f + height) / 2f
                    )
                    page.canvas.drawBitmap(bitmap, null, destination, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    document.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            FileOutputStream(output).use { document.writeTo(it) }
        } finally {
            document.close()
        }
        require(output.isFile && output.length() > 0L) { "The PDF could not be created" }
    }

    fun mergePdfs(context: Context, files: List<File>, output: File) {
        require(files.size >= 2) { "Choose at least two PDF files" }
        require(files.all { it.isFile && it.extension.equals("pdf", true) }) { "One or more files are not valid PDFs" }
        output.parentFile?.mkdirs()
        initPdfBox(context)
        val merger = PDFMergerUtility()
        files.forEach { merger.addSource(it) }
        merger.destinationFileName = output.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        require(output.length() > 0L) { "The merged PDF is empty" }
    }

    fun rewritePdf(context: Context, input: File, output: File) {
        initPdfBox(context)
        PDDocument.load(input).use { document -> document.save(output) }
        require(output.length() > 0L) { "The optimized PDF could not be saved" }
    }

    fun extractPages(context: Context, input: File, expression: String, output: File) {
        initPdfBox(context)
        PDDocument.load(input).use { source ->
            val selected = PageRangeParser.parse(expression, source.numberOfPages)
            PDDocument().use { result ->
                selected.forEach { index -> result.importPage(source.getPage(index)) }
                result.save(output)
            }
        }
        require(output.length() > 0L) { "No pages were exported" }
    }

    fun removePages(context: Context, input: File, expression: String, output: File) {
        initPdfBox(context)
        PDDocument.load(input).use { source ->
            val remove = PageRangeParser.parse(expression, source.numberOfPages).toSet()
            val keep = (0 until source.numberOfPages).filterNot { it in remove }
            require(keep.isNotEmpty()) { "You cannot remove every page" }
            PDDocument().use { result ->
                keep.forEach { index -> result.importPage(source.getPage(index)) }
                result.save(output)
            }
        }
        require(output.length() > 0L) { "The updated PDF could not be saved" }
    }

    fun rotatePages(context: Context, input: File, degrees: Int, output: File) {
        require(degrees in setOf(90, 180, 270)) { "Choose 90, 180 or 270 degrees" }
        initPdfBox(context)
        PDDocument.load(input).use { document ->
            for (page in document.pages) page.rotation = (page.rotation + degrees) % 360
            document.save(output)
        }
        require(output.length() > 0L) { "The rotated PDF could not be saved" }
    }

    fun addWatermark(context: Context, input: File, text: String, output: File) {
        require(text.isNotBlank()) { "Enter watermark text" }
        initPdfBox(context)
        PDDocument.load(input).use { document ->
            document.pages.forEach { page -> addOverlayText(document, page, text.trim(), large = true) }
            document.save(output)
        }
        require(output.length() > 0L) { "The watermarked PDF could not be saved" }
    }

    fun addTextOverlay(context: Context, input: File, text: String, output: File) {
        require(text.isNotBlank()) { "Enter the note to add" }
        initPdfBox(context)
        PDDocument.load(input).use { document ->
            document.pages.forEach { page -> addOverlayText(document, page, text.trim(), large = false) }
            document.save(output)
        }
        require(output.length() > 0L) { "The annotated PDF could not be saved" }
    }

    fun addPageNumbers(context: Context, input: File, startAt: Int, output: File) {
        require(startAt > 0) { "The first page number must be positive" }
        initPdfBox(context)
        PDDocument.load(input).use { document ->
            document.pages.forEachIndexed { index, page ->
                val number = (startAt + index).toString()
                PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 10f)
                    stream.setNonStrokingColor(70, 90, 120)
                    stream.newLineAtOffset((page.mediaBox.width / 2f) - 8f, 22f)
                    stream.showText(number)
                    stream.endText()
                }
            }
            document.save(output)
        }
        require(output.length() > 0L) { "The numbered PDF could not be saved" }
    }

    fun protectPdf(context: Context, input: File, password: String, output: File) {
        require(password.length >= 4) { "Use a password with at least 4 characters" }
        initPdfBox(context)
        PDDocument.load(input).use { document ->
            val permissions = AccessPermission()
            val policy = StandardProtectionPolicy(password, password, permissions)
            policy.encryptionKeyLength = 128
            document.protect(policy)
            document.save(output)
        }
        require(output.length() > 0L) { "The protected PDF could not be saved" }
    }

    fun unlockPdf(context: Context, input: File, password: String, output: File) {
        require(password.isNotBlank()) { "Enter the current password" }
        initPdfBox(context)
        PDDocument.load(input, password).use { document ->
            document.setAllSecurityToBeRemoved(true)
            document.save(output)
        }
        require(output.length() > 0L) { "The unlocked PDF could not be saved" }
    }

    fun extractText(context: Context, input: File, output: File) {
        initPdfBox(context)
        PDDocument.load(input).use { document ->
            val text = PDFTextStripper().getText(document)
            require(text.isNotBlank()) { "No selectable text was found. Scanned pages need OCR first." }
            output.writeText(text, Charsets.UTF_8)
        }
        require(output.isFile && output.length() > 0L) { "Text export failed" }
    }

    fun exportPagesAsPngZip(context: Context, input: File, dpi: Int, output: File) {
        require(dpi in 96..240) { "Choose an image resolution between 96 and 240 DPI" }
        initPdfBox(context)
        PDDocument.load(input).use { document ->
            require(document.numberOfPages <= 120) { "This export is limited to 120 pages at a time" }
            val renderer = PDFRenderer(document)
            ZipOutputStream(FileOutputStream(output)).use { zip ->
                for (index in 0 until document.numberOfPages) {
                    val bitmap = renderer.renderImageWithDPI(index, dpi.toFloat())
                    try {
                        zip.putNextEntry(ZipEntry("page_\${(index + 1).toString().padStart(3, '0')}.png"))
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                        zip.closeEntry()
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }
        require(output.isFile && output.length() > 0L) { "Image export failed" }
    }

    fun updateMetadata(context: Context, input: File, title: String, author: String, output: File) {
        require(title.isNotBlank() || author.isNotBlank()) { "Enter a title or author" }
        initPdfBox(context)
        PDDocument.load(input).use { document ->
            if (title.isNotBlank()) document.documentInformation.title = title.trim()
            if (author.isNotBlank()) document.documentInformation.author = author.trim()
            document.save(output)
        }
        require(output.length() > 0L) { "The updated PDF could not be saved" }
    }

    fun pageCount(context: Context, input: File): Int {
        initPdfBox(context)
        PDDocument.load(input).use { return it.numberOfPages }
    }

    private fun addOverlayText(document: PDDocument, page: PDPage, text: String, large: Boolean) {
        val size = if (large) 28f else 15f
        val y = if (large) page.mediaBox.height / 2f else 48f
        val safeText = text.replace(Regex("[\\\\r\\\\n]+"), " ").take(90)
        PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
            stream.beginText()
            stream.setFont(if (large) PDType1Font.HELVETICA_BOLD else PDType1Font.HELVETICA, size)
            stream.setNonStrokingColor(35, 145, 210)
            stream.newLineAtOffset(48f, y)
            stream.showText(safeText)
            stream.endText()
        }
    }

    private fun initPdfBox(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    private fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= maxHeight) sample *= 2
        return sample.coerceAtLeast(1)
    }
}
