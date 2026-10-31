package com.nexauren.pdfmax.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
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

/**
 * Local document index.
 *
 * The last index is persisted in app-private storage, so it is restored after the
 * process is killed or the device is restarted. MediaStore is used as the primary
 * catalogue. File metadata is read from its cursor instead of stat'ing every path.
 */
object DocumentScanner {
    const val REFRESH_INTERVAL_MS = 20_000L
    private const val CACHE_TTL_MS = REFRESH_INTERVAL_MS
    private const val QUERY_TIMEOUT_MS = 30_000L
    private const val MAX_MEDIASTORE_RESULTS = 20_000
    private const val MAX_FALLBACK_RESULTS = 4_000
    private const val MAX_FALLBACK_DIRECTORIES = 800
    private const val MAX_FALLBACK_DEPTH = 5
    private const val PREFS = "pdfmax_document_index_v2"
    private const val PREF_DOCUMENTS = "documents_json"
    private const val PREF_LAST_SCAN = "last_scan_epoch"
    private const val PREF_LAST_FULL_SCAN = "last_full_scan_epoch"
    private const val PREF_LAST_FALLBACK = "last_fallback_epoch"
    private const val FALLBACK_INTERVAL_MS = 5 * 60 * 1000L

    private val supportedExtensions = setOf(
        "pdf", "doc", "docx", "odt", "rtf",
        "xls", "xlsx", "csv", "ods",
        "ppt", "pptx", "odp",
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "heic",
        "txt", "md", "log",
        "zip", "rar", "7z", "tar", "gz"
    )

    private val cacheLock = Any()
    private val scanLock = Any()

    @Volatile private var cachedFiles: List<IndexedDocument> = emptyList()
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cacheReady: Boolean = false
    @Volatile private var scanInProgress: Boolean = false

    fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /** Fast in-memory access for screens that are already alive. */
    fun cachedDocuments(): List<IndexedDocument> = if (cacheReady) cachedFiles else emptyList()

    /**
     * Restores the last saved index. Call this on Dispatchers.IO before starting
     * a refresh, so previously discovered documents appear immediately on entry.
     */
    fun loadPersistent(context: Context): List<IndexedDocument> {
        if (!hasAccess(context)) return emptyList()
        synchronized(cacheLock) {
            if (cacheReady) return cachedFiles

            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val saved = prefs.getString(PREF_DOCUMENTS, null)
            if (saved.isNullOrBlank()) return emptyList()

            val restored = ArrayList<IndexedDocument>()
            try {
                val json = JSONArray(saved)
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val path = item.optString("path").takeIf { it.isNotBlank() } ?: continue
                    val file = File(path)
                    val extension = item.optString("extension", file.extension)
                        .lowercase(Locale.ROOT)
                    if (extension !in supportedExtensions) continue
                    restored.add(
                        IndexedDocument(
                            file = file,
                            name = item.optString("name", file.name),
                            extension = extension,
                            size = item.optLong("size", 0L).coerceAtLeast(0L),
                            modified = item.optLong("modified", 0L).coerceAtLeast(0L)
                        )
                    )
                }
                cachedFiles = restored.sortedByDescending { it.modified }
                cachedAt = prefs.getLong(PREF_LAST_SCAN, 0L)
                cacheReady = true
                return cachedFiles
            } catch (_: Exception) {
                // A damaged cache is ignored; a new MediaStore index will be created.
                cachedFiles = emptyList()
                cachedAt = 0L
                cacheReady = false
                return emptyList()
            }
        }
    }

    /** Keep the last visible index but force the next call to refresh it. */
    fun invalidate() {
        synchronized(cacheLock) {
            cachedAt = 0L
            cacheReady = cachedFiles.isNotEmpty()
        }
    }

    /**
     * Incremental-cost refresh: Android's MediaStore catalogue is queried, and
     * only a small targeted folder fallback is used when MediaStore has no results.
     * This method must run on a background dispatcher.
     */
    fun scan(context: Context, forceRefresh: Boolean = false): List<IndexedDocument> {
        val appContext = context.applicationContext
        if (!hasAccess(appContext)) return emptyList()

        loadPersistent(appContext)

        synchronized(scanLock) {
            if (scanInProgress) return cachedDocuments()
            scanInProgress = true
        }

        try {
            val now = System.currentTimeMillis()
            if (!forceRefresh && cacheReady && cachedAt > 0L && now - cachedAt < CACHE_TTL_MS) {
                return cachedFiles
            }

            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lastScanAt = prefs.getLong(PREF_LAST_SCAN, 0L)
            val lastFullScanAt = prefs.getLong(PREF_LAST_FULL_SCAN, 0L)
            // A full reconciliation is only needed on first run, manual refresh,
            // or every five minutes. Normal 20-second checks fetch only changed rows.
            val fullScan = forceRefresh || lastFullScanAt == 0L ||
                now - lastFullScanAt >= FALLBACK_INTERVAL_MS
            val modifiedAfter = if (fullScan) null else (lastScanAt - 90_000L).coerceAtLeast(0L)

            val found = LinkedHashMap<String, IndexedDocument>()
            if (!fullScan) {
                cachedFiles.forEach { found[it.file.absolutePath] = it }
            }

            var rowsRead = queryMediaStore(appContext, found, modifiedAfter, extensionFilter = true)
            if (rowsRead <= 0) {
                // Retry with a broad filename query if the OEM provider rejects or
                // ignores the extension-specific selection.
                val broadRows = queryMediaStore(appContext, found, modifiedAfter, extensionFilter = false)
                if (broadRows >= 0) rowsRead = broadRows
            }
            val mediaStoreWorked = rowsRead >= 0
            val lastFallback = prefs.getLong(PREF_LAST_FALLBACK, 0L)
            val shouldFallback = found.isEmpty() ||
                lastFallback == 0L || now - lastFallback >= FALLBACK_INTERVAL_MS

            if (shouldFallback) {
                scanKnownFolders(appContext, found)
                prefs.edit().putLong(PREF_LAST_FALLBACK, now).apply()
            }

            // Nothing changed on the incremental pass: keep the saved index and
            // only advance the last-check timestamp. Avoid rewriting a large JSON cache.
            if (!fullScan && mediaStoreWorked && rowsRead == 0 && !shouldFallback) {
                cachedAt = now
                prefs.edit().putLong(PREF_LAST_SCAN, now).apply()
                return cachedFiles
            }

            val result = found.values.sortedByDescending { it.modified }
            if (mediaStoreWorked && result.isEmpty() && cachedFiles.isNotEmpty()) {
                cachedAt = now
                return cachedFiles
            }
            if (mediaStoreWorked) {
                cachedFiles = result
                cachedAt = now
                cacheReady = true
                persistIndex(
                    appContext,
                    result,
                    now,
                    if (fullScan) now else lastFullScanAt
                )
                return result
            }

            // If MediaStore is temporarily unavailable, preserve the old library.
            // A folder fallback may still add new files, but the next check retries MediaStore.
            if (result.isNotEmpty() && result.size != cachedFiles.size) {
                cachedFiles = result
                cacheReady = true
                cachedAt = now
                persistIndex(appContext, result, lastScanAt, lastFullScanAt)
                return result
            }

            cachedAt = 0L
            return cachedFiles
        } finally {
            synchronized(scanLock) { scanInProgress = false }
        }
    }

    private fun persistIndex(
        context: Context,
        documents: List<IndexedDocument>,
        timestamp: Long,
        fullScanTimestamp: Long
    ) {
        try {
            val json = JSONArray()
            documents.take(MAX_MEDIASTORE_RESULTS).forEach { document ->
                json.put(
                    JSONObject()
                        .put("path", document.file.absolutePath)
                        .put("name", document.name)
                        .put("extension", document.extension)
                        .put("size", document.size)
                        .put("modified", document.modified)
                )
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_DOCUMENTS, json.toString())
                .putLong(PREF_LAST_SCAN, timestamp)
                .putLong(PREF_LAST_FULL_SCAN, fullScanTimestamp)
                .apply()
        } catch (_: Exception) {
            // A failed cache write must never prevent the library from being used.
        }
    }

    @Suppress("DEPRECATION")
    private fun queryMediaStore(
        context: Context,
        destination: MutableMap<String, IndexedDocument>,
        modifiedAfterMillis: Long?,
        extensionFilter: Boolean
    ): Int {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.RELATIVE_PATH
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
        }

        // Avoid provider-specific SQL filename filters. Some OEM MediaStore
        // implementations reject large OR/LIKE expressions, returning no rows.
        // Query the catalogue and filter supported extensions in Kotlin instead.
        val selection = if (modifiedAfterMillis != null) {
            "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?"
        } else null
        val args = if (modifiedAfterMillis != null) {
            arrayOf((modifiedAfterMillis / 1000L).toString())
        } else {
            emptyArray()
        }
        val cancellation = CancellationSignal()
        val handler = Handler(Looper.getMainLooper())
        val timeout = Runnable { cancellation.cancel() }
        handler.postDelayed(timeout, QUERY_TIMEOUT_MS)

        return try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                args.toTypedArray(),
                "${MediaStore.MediaColumns.DATE_MODIFIED} DESC",
                cancellation
            )?.use { cursor ->
                val dataColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val modifiedColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                } else -1

                if (nameColumn < 0) return@use -1

                var count = 0
                var visited = 0
                while (cursor.moveToNext() &&
                    count < MAX_MEDIASTORE_RESULTS &&
                    visited < 300_000
                ) {
                    visited++
                    val name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: continue
                    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (extension !in supportedExtensions) continue

                    val dataPath = if (dataColumn >= 0) cursor.getString(dataColumn) else null
                    val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null
                    val path = when {
                        !dataPath.isNullOrBlank() -> dataPath
                        !relativePath.isNullOrBlank() -> File(
                            Environment.getExternalStorageDirectory(),
                            relativePath + name
                        ).absolutePath
                        else -> continue
                    }

                    val file = File(path)
                    val size = if (sizeColumn >= 0) cursor.getLong(sizeColumn).coerceAtLeast(0L) else 0L
                    val modifiedSeconds = if (modifiedColumn >= 0) cursor.getLong(modifiedColumn) else 0L
                    val modifiedMillis = if (modifiedSeconds > 0L) modifiedSeconds * 1000L else 0L

                    destination[file.absolutePath] = IndexedDocument(
                        file = file,
                        name = name,
                        extension = extension,
                        size = size,
                        modified = modifiedMillis
                    )
                    count++
                }
                count
            } ?: -1
        } catch (_: SecurityException) {
            -1
        } catch (_: IllegalArgumentException) {
            -1
        } catch (_: android.os.OperationCanceledException) {
            -1
        } catch (_: RuntimeException) {
            -1
        } finally {
            handler.removeCallbacks(timeout)
        }
    }

    @Suppress("DEPRECATION")
    private fun scanKnownFolders(
        context: Context,
        destination: MutableMap<String, IndexedDocument>
    ) {
        val storageRoot = Environment.getExternalStorageDirectory()
        val roots = linkedSetOf(
            storageRoot,
            File(storageRoot, Environment.DIRECTORY_DOWNLOADS),
            File(storageRoot, Environment.DIRECTORY_DOCUMENTS),
            File(storageRoot, Environment.DIRECTORY_DCIM),
            File(storageRoot, Environment.DIRECTORY_PICTURES),
            File(storageRoot, Environment.DIRECTORY_MOVIES),
            File(storageRoot, "Bluetooth"),
            File(storageRoot, "WhatsApp"),
            File(storageRoot, "Telegram"),
            File(storageRoot, "Android/media"),
            File(storageRoot, "Android/media/com.whatsapp/WhatsApp/Media"),
            File(storageRoot, "Android/media/org.telegram.messenger/Telegram"),
            File(storageRoot, "Android/media/org.telegram.messenger/Telegram/Telegram Documents")
        )

        val stack = ArrayDeque<Pair<File, Int>>()
        roots.filter { it.isDirectory && it.canRead() }.forEach { stack.add(it to 0) }
        var added = 0
        var visited = 0

        while (stack.isNotEmpty() &&
            added < MAX_FALLBACK_RESULTS &&
            visited < MAX_FALLBACK_DIRECTORIES
        ) {
            val (directory, depth) = stack.removeLast()
            visited++
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
                    if (depth < MAX_FALLBACK_DEPTH &&
                        child.name != "Android" &&
                        !child.name.equals("cache", true)
                    ) {
                        stack.add(child to depth + 1)
                    }
                } else if (child.isFile) {
                    val extension = child.extension.lowercase(Locale.ROOT)
                    if (extension !in supportedExtensions) continue
                    val path = child.absolutePath
                    if (!destination.containsKey(path)) {
                        destination[path] = IndexedDocument(child)
                        added++
                    }
                }
            }
        }
    }
}
