package com.nexauren.pdfmax.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.SystemClock
import android.os.storage.StorageManager
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock

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
    private const val MAX_FALLBACK_RESULTS = 20_000
    private const val MAX_FALLBACK_DIRECTORIES = 20_000
    private const val MAX_FALLBACK_DEPTH = 12
    private const val MAX_FALLBACK_SCAN_MS = 35_000L
    private const val PREFS = "pdfmax_document_index_v2"
    private const val PREF_DOCUMENTS = "documents_json"
    private const val PREF_LAST_SCAN = "last_scan_epoch"
    private const val PREF_LAST_FULL_SCAN = "last_full_scan_epoch"
    private const val PREF_LAST_FALLBACK = "last_fallback_epoch"
    private const val PREF_LAST_DIAGNOSTIC = "last_scan_diagnostic"
    private const val FALLBACK_INTERVAL_MS = 5 * 60 * 1000L

    private val supportedExtensions = setOf(
        "pdf", "doc", "docx", "odt", "rtf",
        "xls", "xlsx", "csv", "ods",
        "ppt", "pptx", "odp",
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "tif", "tiff", "heic",
        "txt", "md", "log",
        "zip", "rar", "7z", "tar", "gz"
    )

    private data class FolderScanStats(
        val rootsFound: Int = 0,
        val directoriesVisited: Int = 0,
        val inaccessibleDirectories: Int = 0,
        val documentsAdded: Int = 0,
        val timedOut: Boolean = false
    )

    @Volatile private var lastDiagnostic = "No scan has run yet."

    private val cacheLock = Any()
    // A second screen must wait for the current scan instead of receiving a false empty result.
    private val scanLock = ReentrantLock()

    @Volatile private var cachedFiles: List<IndexedDocument> = emptyList()
    @Volatile private var cachedAt: Long = 0L
    @Volatile private var cacheReady: Boolean = false

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

    /** Explains what the last discovery pass actually checked, without exposing file names. */
    fun lastScanDiagnostic(english: Boolean): String = lastDiagnostic

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
                cachedAt = if (restored.isNotEmpty()) prefs.getLong(PREF_LAST_SCAN, 0L) else 0L
                cacheReady = restored.isNotEmpty()
                lastDiagnostic = prefs.getString(PREF_LAST_DIAGNOSTIC, lastDiagnostic) ?: lastDiagnostic
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

        // Scan runs only on Dispatchers.IO. Locking here makes concurrent screens wait
        // for the same index operation instead of treating an unfinished scan as "zero files".
        scanLock.lock()
        try {
            val now = System.currentTimeMillis()
            val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

            if (!forceRefresh && cachedFiles.isNotEmpty() && cacheReady &&
                cachedAt > 0L && now - cachedAt < CACHE_TTL_MS
            ) {
                lastDiagnostic = "Using the saved index: ${cachedFiles.size} documents."
                prefs.edit().putString(PREF_LAST_DIAGNOSTIC, lastDiagnostic).apply()
                return cachedFiles
            }

            lastDiagnostic = if (forceRefresh) "Full storage scan started." else "Storage scan started."
            prefs.edit().putString(PREF_LAST_DIAGNOSTIC, lastDiagnostic).apply()

            val lastScanAt = prefs.getLong(PREF_LAST_SCAN, 0L)
            val lastFullScanAt = prefs.getLong(PREF_LAST_FULL_SCAN, 0L)
            // A full reconciliation is only needed on first run, manual refresh,
            // or every five minutes. Normal 20-second checks fetch only changed rows.
            val fullScan = forceRefresh || cachedFiles.isEmpty() || lastFullScanAt == 0L ||
                now - lastFullScanAt >= FALLBACK_INTERVAL_MS
            val modifiedAfter = if (fullScan) null else (lastScanAt - 90_000L).coerceAtLeast(0L)

            val found = LinkedHashMap<String, IndexedDocument>()
            if (!fullScan) {
                cachedFiles.forEach { found[it.file.absolutePath] = it }
            }

            val volumeNames = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                runCatching { MediaStore.getExternalVolumeNames(appContext).toList() }
                    .getOrDefault(emptyList())
                    .toMutableSet()
                    .apply { add(MediaStore.VOLUME_EXTERNAL_PRIMARY) }
            } else {
                setOf("external")
            }

            var mediaStoreRows = 0
            var successfulVolumeQueries = 0
            volumeNames.forEach { volumeName ->
                val rows = queryMediaStore(appContext, found, modifiedAfter, volumeName)
                if (rows >= 0) {
                    successfulVolumeQueries++
                    mediaStoreRows += rows
                }
            }
            val mediaStoreWorked = successfulVolumeQueries > 0
            val rowsRead = if (mediaStoreWorked) mediaStoreRows else -1

            val lastFallback = prefs.getLong(PREF_LAST_FALLBACK, 0L)
            val shouldFallback = found.isEmpty() ||
                lastFallback == 0L || now - lastFallback >= FALLBACK_INTERVAL_MS

            val folderStats = if (shouldFallback) {
                scanKnownFolders(appContext, found).also {
                    prefs.edit().putLong(PREF_LAST_FALLBACK, now).apply()
                }
            } else FolderScanStats()

            lastDiagnostic = buildString {
                append("Scan completed | All-files permission: ").append(if (hasAccess(appContext)) "granted" else "missing")
                append(" | volumes queried: ").append(volumeNames.size)
                append(" | MediaStore documents: ").append(mediaStoreRows)
                append(" | folder roots: ").append(folderStats.rootsFound)
                append(" | folders visited: ").append(folderStats.directoriesVisited)
                append(" | inaccessible folders: ").append(folderStats.inaccessibleDirectories)
                append(" | extra documents: ").append(folderStats.documentsAdded)
                if (folderStats.timedOut) append(" | folder scan reached its time limit")
            }
            // Nothing changed on the incremental pass: keep the saved index and
            // only advance the last-check timestamp. Avoid rewriting a large JSON cache.
            if (!fullScan && mediaStoreWorked && rowsRead == 0 && !shouldFallback &&
                cachedFiles.isNotEmpty()
            ) {
                cachedAt = now
                prefs.edit()
                    .putLong(PREF_LAST_SCAN, now)
                    .putString(PREF_LAST_DIAGNOSTIC, lastDiagnostic)
                    .apply()
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
            if (result.isNotEmpty() && result != cachedFiles) {
                cachedFiles = result
                cacheReady = true
                cachedAt = now
                persistIndex(appContext, result, lastScanAt, lastFullScanAt)
                return result
            }

            cachedAt = 0L
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREF_LAST_DIAGNOSTIC, lastDiagnostic).apply()
            return cachedFiles
        } catch (failure: Exception) {
            lastDiagnostic = "Scan failed: " + (failure.message ?: failure.javaClass.simpleName)
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(PREF_LAST_DIAGNOSTIC, lastDiagnostic).apply()
            throw failure
        } finally {
            scanLock.unlock()
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
                .putString(PREF_LAST_DIAGNOSTIC, lastDiagnostic)
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
        volumeName: String
    ): Int {
        val collection = MediaStore.Files.getContentUri(volumeName)
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
        val selection = modifiedAfterMillis?.let {
            "${MediaStore.MediaColumns.DATE_MODIFIED} >= ?"
        }
        val selectionArgs = modifiedAfterMillis?.let {
            arrayOf((it / 1000L).toString())
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
                selectionArgs,
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

                val volumeRoot = resolveVolumeRoot(context, volumeName)
                var count = 0
                var visited = 0
                while (cursor.moveToNext() && count < MAX_MEDIASTORE_RESULTS && visited < 300_000) {
                    visited++
                    val name = cursor.getString(nameColumn)?.takeIf { it.isNotBlank() } ?: continue
                    val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                    if (extension !in supportedExtensions) continue

                    val dataPath = if (dataColumn >= 0) cursor.getString(dataColumn) else null
                    val relativePath = if (relativePathColumn >= 0) cursor.getString(relativePathColumn) else null
                    val path = when {
                        !dataPath.isNullOrBlank() -> dataPath
                        !relativePath.isNullOrBlank() && volumeRoot != null ->
                            File(volumeRoot, relativePath + name).absolutePath
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
    private fun resolveVolumeRoot(context: Context, volumeName: String): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY ||
            volumeName == MediaStore.VOLUME_EXTERNAL
        ) {
            return Environment.getExternalStorageDirectory()
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            val matched = runCatching {
                storageManager?.storageVolumes?.firstOrNull {
                    it.uuid?.equals(volumeName, ignoreCase = true) == true
                }?.directory
            }.getOrNull()
            if (matched != null) return matched
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun discoverStorageRoots(context: Context): List<File> {
        val roots = linkedSetOf<File>()
        roots.add(Environment.getExternalStorageDirectory())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val storageManager = context.getSystemService(StorageManager::class.java)
            runCatching {
                storageManager?.storageVolumes?.mapNotNullTo(roots) { it.directory }
            }
        }
        // Some OEMs expose removable volumes through getExternalFilesDirs before
        // StorageManager returns a directory. Walk back from Android/data/<app>/files.
        context.getExternalFilesDirs(null).filterNotNull().forEach { appFolder ->
            var root: File? = appFolder
            repeat(4) { root = root?.parentFile }
            root?.let { roots.add(it) }
        }
        return roots.filter { it.exists() && it.isDirectory }
    }

    @Suppress("DEPRECATION")
    private fun scanKnownFolders(
        context: Context,
        destination: MutableMap<String, IndexedDocument>
    ): FolderScanStats {
        val roots = discoverStorageRoots(context)
        val stack = ArrayDeque<Pair<File, Int>>()
        // Add in reverse so the primary shared-storage root is visited first.
        roots.asReversed().forEach { stack.add(it to 0) }
        var added = 0
        var visited = 0
        var inaccessible = 0
        val startedAt = SystemClock.elapsedRealtime()

        while (stack.isNotEmpty() &&
            added < MAX_FALLBACK_RESULTS &&
            visited < MAX_FALLBACK_DIRECTORIES &&
            SystemClock.elapsedRealtime() - startedAt < MAX_FALLBACK_SCAN_MS
        ) {
            val (directory, depth) = stack.removeLast()
            visited++
            val children = try {
                directory.listFiles()
            } catch (_: SecurityException) {
                null
            } catch (_: RuntimeException) {
                null
            }
            if (children == null) {
                inaccessible++
                continue
            }

            for (child in children) {
                if (added >= MAX_FALLBACK_RESULTS) break
                if (child.isHidden || child.name.startsWith(".")) continue

                if (child.isDirectory) {
                    if (child.name.equals("Android", true)) {
                        // Android/data and Android/obb are restricted. Android/media
                        // is shared storage and includes media from other apps.
                        val sharedMedia = File(child, "media")
                        if (sharedMedia.isDirectory) stack.add(sharedMedia to depth + 2)
                        continue
                    }
                    if (depth < MAX_FALLBACK_DEPTH &&
                        !child.name.equals("cache", true) &&
                        !child.name.equals("obb", true)
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

        val timedOut = stack.isNotEmpty() &&
            SystemClock.elapsedRealtime() - startedAt >= MAX_FALLBACK_SCAN_MS
        return FolderScanStats(
            rootsFound = roots.size,
            directoriesVisited = visited,
            inaccessibleDirectories = inaccessible,
            documentsAdded = added,
            timedOut = timedOut
        )
    }
}
