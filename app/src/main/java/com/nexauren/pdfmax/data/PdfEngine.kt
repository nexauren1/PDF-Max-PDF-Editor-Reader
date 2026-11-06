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
import java.io.File
import java.io.FileOutputStream
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
    }

    fun mergePdfs(context: Context, files: List<File>, output: File) {
        require(files.size >= 2) { "Choose at least two PDF files" }
        require(files.all { it.isFile && it.extension.equals("pdf", true) }) {
            "One or more files are not valid PDFs"
        }
        output.parentFile?.mkdirs()
        PDFBoxResourceLoader.init(context.applicationContext)
        val merger = PDFMergerUtility()
        files.forEach { merger.addSource(it) }
        merger.destinationFileName = output.absolutePath
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
    }

    fun rewritePdf(context: Context, input: File, output: File) {
        require(input.isFile && input.extension.equals("pdf", true)) { "Select a valid PDF" }
        output.parentFile?.mkdirs()
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument.load(input).use { document -> document.save(output) }
    }

    private fun sampleSize(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        while (width / (sample * 2) >= maxWidth && height / (sample * 2) >= maxHeight) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }
}
