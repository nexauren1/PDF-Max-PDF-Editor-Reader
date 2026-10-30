package com.nexauren.pdfmax.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object PdfOperations {
    fun outputDirectory(): File {
        @Suppress("DEPRECATION")
        val root = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
        return File(root, "PDF Max").apply { mkdirs() }
    }

    private fun output(input: File, suffix: String, extension: String = "pdf"): File {
        val base = input.nameWithoutExtension.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(outputDirectory(), base + "_" + suffix + "." + extension)
    }

    private fun init(context: Context) = PDFBoxResourceLoader.init(context.applicationContext)

    fun merge(context: Context, inputs: List<File>): File {
        require(inputs.size >= 2) { "Selecione pelo menos dois PDFs." }
        init(context)
        val target = File(outputDirectory(), "PDF_Max_Merged_" + System.currentTimeMillis() + ".pdf")
        val merger = PDFMergerUtility()
        inputs.forEach { require(it.extension.equals("pdf", true)); merger.addSource(it) }
        merger.destinationFileName = target.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupMainMemoryOnly())
        return target
    }

    fun split(context: Context, input: File, firstPage: Int, lastPage: Int): List<File> {
        init(context)
        PDDocument.load(input).use { source ->
            val start = (firstPage - 1).coerceAtLeast(0)
            val end = (lastPage - 1).coerceAtMost(source.numberOfPages - 1)
            require(start <= end && start < source.numberOfPages) { "Intervalo de páginas inválido." }
            return (start..end).map { index ->
                PDDocument().use { one ->
                    one.importPage(source.getPage(index))
                    val target = output(input, "page_" + (index + 1))
                    one.save(target)
                    target
                }
            }
        }
    }

    fun rotate(context: Context, input: File, degrees: Int): File {
        init(context)
        require(degrees in listOf(90, 180, 270))
        val target = output(input, "rotated")
        PDDocument.load(input).use { doc ->
            for (page in doc.pages) page.rotation = (page.rotation + degrees) % 360
            doc.save(target)
        }
        return target
    }

    fun addText(context: Context, input: File, text: String, watermark: Boolean = false, numbering: Boolean = false): File {
        init(context)
        require(text.isNotBlank() || numbering) { "Escreva o texto que deseja adicionar." }
        val target = output(input, if (watermark) "watermark" else if (numbering) "numbered" else "edited")
        PDDocument.load(input).use { doc ->
            val pageCount = doc.numberOfPages
            for (index in 0 until pageCount) {
                val page = doc.getPage(index)
                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA_BOLD, if (watermark) 28f else 12f)
                    stream.setNonStrokingColor(40, 120, 220)
                    val label = when {
                        numbering -> (index + 1).toString() + " / " + pageCount
                        else -> text
                    }
                    stream.newLineAtOffset(if (watermark) page.mediaBox.width / 2f - 90f else 42f, if (watermark) page.mediaBox.height / 2f else 42f)
                    stream.showText(latin(label).take(180))
                    stream.endText()
                }
            }
            doc.save(target)
        }
        return target
    }

    fun protect(context: Context, input: File, password: String): File {
        init(context)
        require(password.length >= 4) { "A senha deve ter pelo menos 4 caracteres." }
        val target = output(input, "protected")
        PDDocument.load(input).use { doc ->
            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 128
            doc.protect(policy)
            doc.save(target)
        }
        return target
    }

    fun compress(context: Context, input: File): File {
        init(context)
        val target = output(input, "optimized")
        PDDocument.load(input).use { it.save(target) }
        return target
    }

    fun repair(context: Context, input: File): File {
        init(context)
        val target = output(input, "repaired")
        PDDocument.load(input).use { it.save(target) }
        return target
    }

    fun imageToPdf(context: Context, image: File): File {
        init(context)
        val target = File(outputDirectory(), image.nameWithoutExtension + "_PDF_Max.pdf")
        val bitmap = BitmapFactory.decodeFile(image.absolutePath) ?: error("Não foi possível abrir a imagem.")
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            val imageObject = LosslessFactory.createFromImage(doc, bitmap)
            val pageW = page.mediaBox.width
            val pageH = page.mediaBox.height
            val scale = minOf((pageW - 48f) / bitmap.width, (pageH - 48f) / bitmap.height)
            val width = bitmap.width * scale
            val height = bitmap.height * scale
            PDPageContentStream(doc, page).use { it.drawImage(imageObject, (pageW - width) / 2f, (pageH - height) / 2f, width, height) }
            doc.save(target)
        }
        bitmap.recycle()
        return target
    }

    fun pdfToImages(context: Context, input: File): List<File> {
        val results = mutableListOf<File>()
        val descriptor = android.os.ParcelFileDescriptor.open(input, android.os.ParcelFileDescriptor.MODE_READ_ONLY)
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                for (index in 0 until renderer.pageCount) {
                    renderer.openPage(index).use { page ->
                        val scale = minOf(2f, 2200f / page.width)
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val file = output(input, "page_" + (index + 1), "jpg")
                        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                        bitmap.recycle()
                        results.add(file)
                    }
                }
            }
        }
        return results
    }

    fun extractImages(context: Context, input: File): List<File> {
        init(context)
        val results = mutableListOf<File>()
        PDDocument.load(input).use { doc ->
            for (pageIndex in 0 until doc.numberOfPages) {
                val resources = doc.getPage(pageIndex).resources
                for (name in resources.xObjectNames) {
                    val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                    if (xObject is PDImageXObject) {
                        val bitmap = xObject.image
                        val file = File(outputDirectory(), input.nameWithoutExtension + "_image_" + (pageIndex + 1) + "_" + (results.size + 1) + ".png")
                        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        results.add(file)
                    }
                }
            }
        }
        return results
    }

    fun extractText(context: Context, input: File): String {
        init(context)
        return PDDocument.load(input).use { PDFTextStripper().getText(it) }
    }

    fun compare(context: Context, first: File, second: File): String {
        val a = extractText(context, first).lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val b = extractText(context, second).lines().map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val added = b - a
        val removed = a - b
        return buildString {
            appendLine("Linhas adicionadas: " + added.size)
            added.take(30).forEach { appendLine("+ " + it) }
            appendLine()
            appendLine("Linhas removidas: " + removed.size)
            removed.take(30).forEach { appendLine("- " + it) }
        }
    }

    fun convertDocxToPdf(context: Context, input: File): File {
        val text = OfficeTextExtractor.extract(input)
        require(text.isNotBlank()) { "O DOCX não contém texto legível." }
        return createPdfFromText(context, input.nameWithoutExtension, text)
    }

    fun pdfToDocx(context: Context, input: File): File {
        val text = extractText(context, input)
        val target = output(input, "converted", "docx")
        writeDocx(target, text)
        return target
    }

    fun createPdfFromText(context: Context, title: String, text: String): File {
        init(context)
        val target = File(outputDirectory(), title.replace(Regex("[^A-Za-z0-9_-]"), "_") + "_PDF_Max.pdf")
        PDDocument().use { doc ->
            var page = PDPage(PDRectangle.A4)
            doc.addPage(page)
            var stream = PDPageContentStream(doc, page)
            var y = page.mediaBox.height - 54f
            stream.beginText()
            stream.setFont(PDType1Font.HELVETICA, 11f)
            stream.newLineAtOffset(42f, y)
            val lines = text.replace("\r", "").split("\n").flatMap { wrap(latin(it), 92) }
            for (line in lines) {
                if (y < 55f) {
                    stream.endText()
                    stream.close()
                    page = PDPage(PDRectangle.A4)
                    doc.addPage(page)
                    stream = PDPageContentStream(doc, page)
                    y = page.mediaBox.height - 54f
                    stream.beginText()
                    stream.setFont(PDType1Font.HELVETICA, 11f)
                    stream.newLineAtOffset(42f, y)
                }
                stream.showText(line.ifBlank { " " })
                stream.newLineAtOffset(0f, -15f)
                y -= 15f
            }
            stream.endText()
            stream.close()
            doc.documentInformation.title = title
            doc.save(target)
        }
        return target
    }

    fun recognizeImageToPdf(context: Context, imageFile: File): File {
        val image = InputImage.fromFilePath(context, Uri.fromFile(imageFile))
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            val result = Tasks.await(recognizer.process(image))
            createPdfFromText(context, imageFile.nameWithoutExtension + "_OCR", result.text)
        } finally {
            recognizer.close()
        }
    }

    fun getProperties(context: Context, input: File): String {
        init(context)
        return PDDocument.load(input).use { doc ->
            val info = doc.documentInformation
            "Ficheiro: " + input.name + "\nPáginas: " + doc.numberOfPages +
                "\nTamanho: " + input.length() + " bytes\nTítulo: " + (info.title ?: "—") +
                "\nAutor: " + (info.author ?: "—") + "\nCriado: " + (info.creationDate?.time ?: "—")
        }
    }

    private fun writeDocx(target: File, text: String) {
        val paragraphs = text.lines().joinToString("") { line ->
            "<w:p><w:r><w:t xml:space=\"preserve\">" + xmlEscape(line) + "</w:t></w:r></w:p>"
        }
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            fun entry(name: String, value: String) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(value.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            entry("[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>")
            entry("_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>")
            entry("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>" + paragraphs + "<w:sectPr/></w:body></w:document>")
        }
    }

    private fun xmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    private fun latin(value: String) = value.map { if (it.code in 32..126 || it == '\n' || it == '\t') it else ' ' }.joinToString("")
    private fun wrap(value: String, max: Int): List<String> = if (value.isEmpty()) listOf("") else value.chunked(max)
}
