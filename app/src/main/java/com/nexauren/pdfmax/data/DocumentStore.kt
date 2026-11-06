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
    val trashDirectory = File(appContext.filesDir, "trash").apply { mkdirs() }
    private val preferences = appContext.getSharedPreferences("pdfmax_workspace", Context.MODE_PRIVATE)

    fun listDocuments(): List<PdfDocumentItem> = documentsDirectory.listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("pdf", ignoreCase = true) }
        .sortedByDescending { it.lastModified() }
        .map(::PdfDocumentItem)

    fun listTrash(): List<PdfDocumentItem> = trashDirectory.listFiles().orEmpty()
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
        val target = uniqueFile(documentsDirectory, safeName)
        val input = resolver.openInputStream(uri) ?: error("Unable to read the selected document")
        input.use { source -> target.outputStream().use { sink -> source.copyTo(sink) } }
        require(target.length() > 0L) { "The selected file is empty" }
        require(target.inputStream().use { inputStream ->
            val signature = ByteArray(5)
            inputStream.read(signature) == 5 && String(signature, Charsets.US_ASCII) == "%PDF-"
        }) { "The selected file is not a valid PDF document" }
        return target
    }

    fun newPdfFile(prefix: String): File = newOutputFile(prefix, "pdf")

    fun newOutputFile(prefix: String, extension: String): File {
        val safeExtension = extension.trim().removePrefix(".").lowercase(Locale.US)
        require(safeExtension.matches(Regex("[a-z0-9]{1,8}"))) { "Invalid output file extension" }
        val safePrefix = prefix.replace(Regex("[^A-Za-z0-9_-]"), "_").take(48).ifBlank { "PDF_Max" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        var target = File(documentsDirectory, safePrefix + "_" + stamp + "." + safeExtension)
        var suffix = 2
        while (target.exists()) {
            target = File(documentsDirectory, safePrefix + "_" + stamp + "_" + suffix + "." + safeExtension)
            suffix++
        }
        return target
    }

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

    fun recentPaths(): List<String> = preferences.getString("recent", "")
        .orEmpty().split("\n").filter { it.isNotBlank() }

    fun recordOpened(file: File) {
        val recent = (listOf(file.absolutePath) + recentPaths().filterNot { it == file.absolutePath }).take(60)
        preferences.edit().putString("recent", recent.joinToString("\n")).apply()
    }

    fun moveToTrash(file: File) {
        require(file.parentFile?.canonicalFile == documentsDirectory.canonicalFile) {
            "Only files saved inside PDF Max can be moved to Trash"
        }
        val destination = uniqueFile(trashDirectory, file.name)
        check(file.renameTo(destination)) { "Unable to move the document to Trash" }
        preferences.edit()
            .putStringSet("favorites", favoritePaths() - file.absolutePath)
            .putString("recent", recentPaths().filterNot { it == file.absolutePath }.joinToString("\n"))
            .apply()
    }

    fun restoreFromTrash(file: File) {
        require(file.parentFile?.canonicalFile == trashDirectory.canonicalFile) {
            "Only documents in Trash can be restored"
        }
        val destination = uniqueFile(documentsDirectory, file.name)
        check(file.renameTo(destination)) { "Unable to restore the document" }
    }

    fun permanentlyDelete(file: File) {
        require(file.parentFile?.canonicalFile == trashDirectory.canonicalFile) {
            "Only documents in Trash can be permanently deleted"
        }
        check(file.delete()) { "Unable to delete the document" }
    }

    fun storageUsedBytes(): Long =
        (documentsDirectory.listFiles().orEmpty().toList() + trashDirectory.listFiles().orEmpty().toList())
            .filter { it.isFile }.sumOf { it.length() }

    private fun uniqueFile(directory: File, name: String): File {
        val extension = name.substringAfterLast('.', "")
        val base = if (extension.isNotEmpty()) name.dropLast(extension.length + 1) else name
        val suffix = if (extension.isNotEmpty()) "." + extension else ""
        var candidate = File(directory, base + suffix)
        var number = 2
        while (candidate.exists()) {
            candidate = File(directory, base + "_" + number + suffix)
            number++
        }
        return candidate
    }
}
