package com.nexauren.pdfmax.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File
import java.util.Locale

enum class DocumentKind(val labelPt: String, val labelEn: String) {
    PDF("PDF", "PDF"), WORD("Word", "Word"), EXCEL("Excel", "Excel"),
    POWERPOINT("PowerPoint", "PowerPoint"), IMAGE("Imagens", "Images"),
    TEXT("Texto", "Text"), ARCHIVE("Arquivos compactados", "Archives"), OTHER("Outros", "Other")
}

data class IndexedDocument(
    val file: File,
    val name: String = file.name,
    val extension: String = file.extension.lowercase(Locale.ROOT),
    val size: Long = file.length(),
    val modified: Long = file.lastModified(),
    val kind: DocumentKind = classify(file.extension)
) {
    companion object {
        fun classify(raw: String): DocumentKind = when (raw.lowercase(Locale.ROOT)) {
            "pdf" -> DocumentKind.PDF
            "doc", "docx", "odt", "rtf" -> DocumentKind.WORD
            "xls", "xlsx", "csv", "ods" -> DocumentKind.EXCEL
            "ppt", "pptx", "odp" -> DocumentKind.POWERPOINT
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "heic" -> DocumentKind.IMAGE
            "txt", "md", "log" -> DocumentKind.TEXT
            "zip", "rar", "7z", "tar", "gz" -> DocumentKind.ARCHIVE
            else -> DocumentKind.OTHER
        }
    }
    val sizeLabel: String
        get() = when {
            size >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", size / (1024f * 1024f))
            size >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", size / 1024f)
            else -> "$size B"
        }
}

object DocumentScanner {
    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    @Suppress("DEPRECATION")
    fun scan(context: Context): List<IndexedDocument> {
        val roots = buildList {
            add(Environment.getExternalStorageDirectory())
            context.getExternalFilesDirs(null).filterNotNull().forEach { appDir ->
                var root: File? = appDir
                repeat(4) { root = root?.parentFile }
                root?.let { add(it) }
            }
        }.distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
        val results = ArrayList<IndexedDocument>()
        val stack = ArrayDeque<Pair<File, Int>>()
        roots.forEach { stack.add(it to 0) }
        val maxFiles = 20_000
        val maxDepth = 12
        val supported = setOf("pdf", "doc", "docx", "odt", "rtf", "xls", "xlsx", "csv", "ods", "ppt", "pptx", "odp", "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "heic", "txt", "md", "log", "zip", "rar", "7z", "tar", "gz")
        while (stack.isNotEmpty() && results.size < maxFiles) {
            val (directory, depth) = stack.removeLast()
            val children = try { directory.listFiles() } catch (_: SecurityException) { null } ?: continue
            for (child in children) {
                if (results.size >= maxFiles) break
                if (child.isHidden || child.name.startsWith(".")) continue
                if (child.isDirectory) {
                    if (depth < maxDepth && child.name == "Android") {
                        val media = File(child, "media")
                        if (media.isDirectory && media.canRead()) stack.add(media to depth + 1)
                    } else if (depth < maxDepth && child.canRead()) {
                        stack.add(child to depth + 1)
                    }
                } else if (child.isFile && child.canRead() && child.extension.lowercase(Locale.ROOT) in supported) {
                    results.add(IndexedDocument(child))
                }
            }
        }
        return results.sortedByDescending { it.modified }
    }
}
