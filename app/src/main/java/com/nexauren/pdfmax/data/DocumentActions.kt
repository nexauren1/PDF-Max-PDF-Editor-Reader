package com.nexauren.pdfmax.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/** Android print/share hand-off. Source-file discovery and selection remain inside PDF Max. */
object DocumentActions {
    fun createShareCopy(context: Context, source: File): File {
        require(source.isFile && source.canRead()) { "Não foi possível ler o ficheiro para partilhar." }
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val copy = File(directory, System.currentTimeMillis().toString() + "_" + safeName)
        source.copyTo(copy, overwrite = true)
        return copy
    }

    fun share(context: Context, file: File) {
        require(file.isFile && file.canRead()) { "O ficheiro para partilhar não está disponível." }
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
        val mimeType = when (file.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "*/*"
        }
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(sendIntent, "PDF Max").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun print(context: Context, file: File) {
        require(file.isFile && file.canRead()) { "O PDF para impressão não está disponível." }
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("O serviço de impressão não está disponível neste dispositivo.")
        val attributes = PrintAttributes.Builder()
            .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
            .setResolution(PrintAttributes.Resolution("pdfmax", "PDF Max", 300, 300))
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .build()
        manager.print(file.name, PdfFilePrintAdapter(file), attributes)
    }

    private class PdfFilePrintAdapter(private val file: File) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes?,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(file.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal,
            callback: WriteResultCallback
        ) {
            try {
                if (cancellationSignal.isCanceled) {
                    callback.onWriteCancelled()
                    return
                }
                FileInputStream(file).use { input ->
                    FileOutputStream(destination.fileDescriptor).use { output ->
                        input.copyTo(output)
                        output.flush()
                    }
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (error: Exception) {
                callback.onWriteFailed(error.message ?: "Falha ao preparar o PDF para impressão.")
            } finally {
                runCatching { destination.close() }
            }
        }
    }
}
