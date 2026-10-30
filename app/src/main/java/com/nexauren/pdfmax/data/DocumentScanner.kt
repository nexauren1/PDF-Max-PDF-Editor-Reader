package com.nexauren.pdfmax.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
    private const val CACHE_TTL_MS = 30_000L
    private const val MAX_MEDIASTORE_RESULTS = 30_000
    private const val MAX_FALLBACK_RESULTS = 4_000
    private const val MAX_FALLBACK_DEPTH = 4

    private val supportedExtensions = setOf(
        "pdf", "doc", "docx", "odt", "rtf",
        "xls", "xlsx", "csv", "ods",
        "ppt", "pptx", "odp",
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "heic",
        "txt", "md", "log",
        "zip", "rar", "7z", "tar", "gz"
    )

    private val cacheLock = Any()

    @Volatile
    private var cachedFiles: List<IndexedDocument> = emptyList()

    @Volatile
    private var cachedAt: Long = 0L

    @Volatile
    private var cacheReady: Boolean = false

    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Returns the latest in-memory index without starting a new storage scan. */
    fun cachedDocuments(): List<IndexedDocument> = if (cacheReady) cachedFiles else emptyList()

    /** Call after creating, deleting or renaming a file from PDF Max. */
    fun invalidate() {
        synchronized(cacheLock) {
            cacheReady = false
            cachedAt = 0L
        }
    }

    /**
     * Fast document discovery:
     * 1. Android MediaStore index (system-maintained; avoids walking the entire disk).
     * 2. Small targeted folder scan for newly created files and folders not yet indexed.
     *
     * Must be called from a background dispatcher. A short TTL prevents repeated full
     * discovery when Compose screens resume or multiple tools open in sequence.
     */
    fun scan(context: Context, forceRefresh: Boolean = false): List<IndexedDocument> {
        if (!hasAccess(context)) {
            invalidate()
            return emptyList()
        }

        synchronized(cacheLock) {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cacheReady && now - cachedAt < CACHE_TTL_MS) {
                return cachedFiles
            }

            val appContext = context.applicationContext
            val found = LinkedHashMap<String, IndexedDocument>()

            queryMediaStore(appContext, found)
            scanKnownFolders(appContext, found)

            val result = found.values.sortedByDescending { it.modified }
            cachedFiles = result
            cachedAt = System.currentTimeMillis()
            cacheReady = true
            return result
        }
    }

    @Suppress("DEPRECATION")
    private fun queryMediaStore(
        context: Context,
        destination: MutableMap<String, IndexedDocument>
    ) {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_MODIFIED
        )
        val selection = supportedExtensions.joinToString(" OR ") {
            "\${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        }
        val selectionArgs = supportedExtensions.map { "%.$it" }.toTypedArray()
        val sortOrder = "\${MediaStore.MediaColumns.DATE_MODIFIED} DESC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val pathColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)

                if (pathColumn < 0 || nameColumn < 0) return@use

                var count = 0
                while (cursor.moveToNext() && count < MAX_MEDIASTORE_RESULTS) {
                    val path = cursor.getString(pathColumn)?.takeIf { it.isNotBlank() } ?: continue
                    val file = File(path)
                    val extension = file.extension.lowercase(Locale.ROOT)
                    if (extension !in supportedExtensions || !file.isFile || !file.canRead()) continue

                    val canonicalPath = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)
                    if (destination.containsKey(canonicalPath)) continue

                    val name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: file.name
                    val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn).coerceAtLeast(0L) else file.length()
                    // MediaStore DATE_MODIFIED is seconds since epoch; File.lastModified is milliseconds.
                    val modifiedSeconds = if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0L
                    val modifiedMillis = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else file.lastModified()

                    destination[canonicalPath] = IndexedDocument(
                        file = file,
                        name = name,
                        extension = extension,
                        size = if (size > 0L) size else file.length(),
                        modified = modifiedMillis
                    )
                    count++
                }
            }
        } catch (_: SecurityException) {
            // The targeted folder fallback still finds any locations Android exposes.
        } catch (_: IllegalArgumentException) {
            // Some OEM MediaStore providers reject the DATA projection; use the fallback scan.
        } catch (_: RuntimeException) {
            // Keep the library usable on devices with incomplete MediaStore implementations.
        }
    }

    @Suppress("DEPRECATION")
    private fun scanKnownFolders(
        context: Context,
        destination: MutableMap<String, IndexedDocument>
    ) {
        val storageRoot = Environment.getExternalStorageDirectory()
        val roots = linkedSetOf(
            File(storageRoot, Environment.DIRECTORY_DOWNLOADS),
            File(storageRoot, Environment.DIRECTORY_DOCUMENTS),
            File(storageRoot, Environment.DIRECTORY_DCIM),
            File(storageRoot, Environment.DIRECTORY_PICTURES),
            File(storageRoot, Environment.DIRECTORY_MOVIES),
            File(storageRoot, "Bluetooth"),
            File(storageRoot, "WhatsApp"),
            File(storageRoot, "Telegram"),
            File(storageRoot, "Android/media"),
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "PDF Max")
        )

        val stack = ArrayDeque<Pair<File, Int>>()
        roots.filter { it.isDirectory && it.canRead() }.forEach { stack.add(it to 0) }
        var added = 0

        while (stack.isNotEmpty() && added < MAX_FALLBACK_RESULTS) {
            val (directory, depth) = stack.removeLast()
            val children = try {
                directory.listFiles()
            } catch (_: SecurityException) {
                null
            } catch (_: RuntimeException) {
                null
            } ?: continue

            for (child in children) {
                if (added >= MAX_FALLBACK_RESULTS) break
                if (child.isHidden || child.name.startsWith(".")) continue

                if (child.isDirectory) {
                    if (depth < MAX_FALLBACK_DEPTH && child.name != "Android") {
                        stack.add(child to depth + 1)
                    }
                } else if (child.isFile && child.canRead()) {
                    val extension = child.extension.lowercase(Locale.ROOT)
                    if (extension !in supportedExtensions) continue

                    val canonicalPath = runCatching { child.canonicalPath }.getOrDefault(child.absolutePath)
                    if (destination.containsKey(canonicalPath)) continue

                    destination[canonicalPath] = IndexedDocument(child)
                    added++
                }
            }
        }
    }
}
