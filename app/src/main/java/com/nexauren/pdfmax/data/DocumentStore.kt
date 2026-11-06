package com.nexauren.pdfmax.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PdfDocumentItem(
    val file: File,
    val name: String = file.name,
    val sizeBytes: Long = file.length(),
    val modifiedAt: Long = file.lastModified()
) {
    val formattedSize: String
        get() = when {
            sizeBytes >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", sizeBytes / (1024f * 1024f))
            sizeBytes >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", sizeBytes / 1024f)
            else -> "$sizeBytes B"
        }

    val formattedDate: String
        get() = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(modifiedAt))
}

class DocumentStore(context: Context) {
    private val appContext = context.applicationContext
    val documentsDirectory = File(appContext.filesDir, "documents").apply { mkdirs() }
    private val preferences = appContext.getSharedPreferences("pdfmax_v2", Context.MODE_PRIVATE)

    fun listDocuments(): List<PdfDocumentItem> =
        documentsDirectory.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
            .sortedByDescending { it.lastModified() }
            .map(::PdfDocumentItem)

    fun importPdf(uri: Uri): File {
        val resolver = appContext.contentResolver
        val suggestedName = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "Imported_document.pdf"
        val cleaned = suggestedName.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        val safeName = if (cleaned.endsWith(".pdf", true)) cleaned else "$cleaned.pdf"
        val target = uniqueFile(safeName)
        val input = resolver.openInputStream(uri) ?: error("Unable to read the selected document")
        input.use { source ->
            target.outputStream().use { sink -> source.copyTo(sink) }
        }
        require(target.length() > 0L) { "The selected file is empty" }
        return target
    }

    fun newPdfFile(prefix: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(documentsDirectory, "${prefix}_${stamp}.pdf")
    }

    fun isFavorite(file: File): Boolean = favoritePaths().contains(file.absolutePath)

    fun favoritePaths(): Set<String> =
        preferences.getStringSet("favorites", emptySet()).orEmpty().toSet()

    fun toggleFavorite(file: File): Boolean {
        val updated = favoritePaths().toMutableSet()
        val nowFavorite = if (updated.contains(file.absolutePath)) {
            updated.remove(file.absolutePath)
            false
        } else {
            updated.add(file.absolutePath)
            true
        }
        preferences.edit().putStringSet("favorites", updated).apply()
        return nowFavorite
    }

    fun storageUsedBytes(): Long =
        documentsDirectory.listFiles().orEmpty().filter { it.isFile }.sumOf { it.length() }

    fun deleteDocument(file: File) {
        require(file.parentFile?.canonicalFile == documentsDirectory.canonicalFile) {
            "Only PDF Max documents can be removed here"
        }
        file.delete()
        val updated = favoritePaths().toMutableSet().apply { remove(file.absolutePath) }
        preferences.edit().putStringSet("favorites", updated).apply()
    }

    private fun uniqueFile(name: String): File {
        val extension = ".pdf"
        val base = if (name.endsWith(extension, true)) name.dropLast(extension.length) else name
        var candidate = File(documentsDirectory, "$base.pdf")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(documentsDirectory, "${base}_${suffix}.pdf")
            suffix++
        }
        return candidate
    }
}
