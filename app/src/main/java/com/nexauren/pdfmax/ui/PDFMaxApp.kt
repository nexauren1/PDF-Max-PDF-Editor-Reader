package com.nexauren.pdfmax.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nexauren.pdfmax.data.DocumentStore
import com.nexauren.pdfmax.data.PdfDocumentItem
import com.nexauren.pdfmax.data.PdfEngine
import com.nexauren.pdfmax.ui.theme.PDFMaxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

private enum class Screen {
    ONBOARDING, HOME, FILES, TOOLS, FAVORITES, RECENT, TRASH, CREATE, COMPRESS,
    MERGE, READER, WORKSPACE, SETTINGS, PROCESSING, RESULT
}

private data class ResultFile(
    val file: File,
    val title: String,
    val beforeBytes: Long? = null,
    val mimeType: String = "application/pdf"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDFMaxApp(initialPdf: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { DocumentStore(context) }
    val prefs = remember(context) { context.getSharedPreferences("pdfmax_workspace", Context.MODE_PRIVATE) }

    var dark by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
    var english by rememberSaveable { mutableStateOf(prefs.getBoolean("english", false)) }
    var screen by rememberSaveable {
        mutableStateOf(if (prefs.getBoolean("onboarding_done", false)) Screen.HOME else Screen.ONBOARDING)
    }
    var welcomePage by rememberSaveable { mutableIntStateOf(0) }
    var files by remember { mutableStateOf(store.listDocuments()) }
    var trash by remember { mutableStateOf(store.listTrash()) }
    var favorites by remember { mutableStateOf(store.favoritePaths()) }
    var recentPaths by remember { mutableStateOf(store.recentPaths()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var mergeSelection by remember { mutableStateOf(listOf<String>()) }
    var images by remember { mutableStateOf(listOf<Uri>()) }
    var result by remember { mutableStateOf<ResultFile?>(null) }
    var operationTitle by remember { mutableStateOf("") }
    var operationDetail by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var activeToolId by rememberSaveable { mutableStateOf("extract_pages") }
    var showQuick by remember { mutableStateOf(false) }
    var navigationBusy by remember { mutableStateOf(false) }
    var navigationLabel by remember { mutableStateOf(if (english) "Opening workspace" else "A abrir espaço de trabalho") }
    var processingBusy by remember { mutableStateOf(false) }
    var navigationJob by remember { mutableStateOf<Job?>(null) }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }
    val gearAnimation = rememberInfiniteTransition(label = "PDF Max gear")
    val gearAngle by gearAnimation.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1050, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "gear rotation"
    )

    fun refresh() {
        files = store.listDocuments()
        trash = store.listTrash()
        favorites = store.favoritePaths()
        recentPaths = store.recentPaths()
    }

    fun go(to: Screen) {
        if (to == screen && !navigationBusy) {
            if (drawer.isOpen) scope.launch { drawer.close() }
            return
        }
        navigationJob?.cancel()
        navigationJob = scope.launch {
            navigationLabel = if (english) "Preparing " + to.name.lowercase(Locale.ROOT) else "A preparar " + to.name.lowercase(Locale.ROOT)
            navigationBusy = true
            delay(390)
            screen = to
            drawer.close()
            delay(90)
            navigationBusy = false
        }
    }

    fun openPdf(file: File) {
        selectedFile = file
        store.recordOpened(file)
        refresh()
        go(Screen.READER)
    }

    fun shareFile(file: File, mimeType: String = "application/pdf", whatsappOnly: Boolean = false) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (whatsappOnly) setPackage("com.whatsapp")
            }
            if (whatsappOnly && send.resolveActivity(context.packageManager) == null) {
                shareFile(file, mimeType, false)
            } else {
                context.startActivity(Intent.createChooser(send, if (english) "Share file" else "Partilhar ficheiro"))
            }
        }.onFailure {
            scope.launch { snackbar.showSnackbar(it.message ?: if (english) "Unable to share this file" else "Não foi possível partilhar este ficheiro") }
        }
    }

    fun toggleFavorite(file: File) {
        store.toggleFavorite(file)
        refresh()
    }

    fun startOperation(
        title: String,
        detail: String,
        beforeBytes: Long? = null,
        mimeType: String = "application/pdf",
        work: suspend () -> File
    ) {
        if (processingBusy) return
        processingBusy = true
        operationTitle = title
        operationDetail = detail
        navigationLabel = if (english) "Preparing operation" else "A preparar operação"
        scope.launch {
            navigationBusy = true
            delay(360)
            screen = Screen.PROCESSING
            drawer.close()
            navigationBusy = false
            val started = System.currentTimeMillis()
            try {
                val output = work()
                require(output.isFile && output.length() > 0L) { "The operation did not create a valid output file" }
                val minimumDisplay = 700L
                val remaining = minimumDisplay - (System.currentTimeMillis() - started)
                if (remaining > 0) {
                    operationDetail = if (english) "Finalizing and checking the output file…" else "A finalizar e verificar o ficheiro de saída…"
                    delay(remaining)
                }
                result = ResultFile(output, title, beforeBytes, mimeType)
                refresh()
                navigationLabel = if (english) "Preparing your result" else "A preparar o resultado"
                navigationBusy = true
                delay(350)
                screen = Screen.RESULT
                navigationBusy = false
            } catch (error: Throwable) {
                navigationBusy = true
                delay(300)
                screen = Screen.HOME
                navigationBusy = false
                snackbar.showSnackbar(error.message ?: if (english) "The operation failed" else "A operação falhou")
            } finally {
                processingBusy = false
            }
        }
    }

    fun createPdf() {
        if (images.isEmpty()) return
        startOperation(
            if (english) "PDF created" else "PDF criado",
            if (english) "Preparing pages from the selected photos" else "A preparar as páginas das fotografias selecionadas"
        ) {
            val output = store.newPdfFile("Photo_to_PDF")
            withContext(Dispatchers.IO) { PdfEngine.createPdfFromImages(context, images, output) }
            output
        }
    }

    fun compressPdf(file: File) {
        val before = file.length()
        startOperation(
            if (english) "Compress PDF" else "Comprimir PDF",
            if (english) "Rebuilding the document and checking the actual output size" else "A reconstruir o documento e a verificar o tamanho real do resultado",
            before
        ) {
            val output = store.newPdfFile(file.nameWithoutExtension + "_compressed")
            withContext(Dispatchers.IO) { PdfEngine.rewritePdf(context, file, output) }
            output
        }
    }

    fun mergePdfs() {
        val selected = mergeSelection.mapNotNull { path -> files.firstOrNull { it.file.absolutePath == path }?.file }
        if (selected.size < 2) return
        startOperation(
            if (english) "PDFs merged" else "PDFs unidos",
            if (english) "Combining " + selected.size + " documents in the chosen order" else "A combinar " + selected.size + " documentos pela ordem escolhida"
        ) {
            val output = store.newPdfFile("Merged_document")
            withContext(Dispatchers.IO) { PdfEngine.mergePdfs(context, selected, output) }
            output
        }
    }

    fun runAdvancedTool(toolId: String, file: File, options: Map<String, String>) {
        val outputExtension = when (toolId) {
            "extract_text" -> "txt"
            "to_images" -> "zip"
            else -> "pdf"
        }
        val outputMime = when (outputExtension) {
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            else -> "application/pdf"
        }
        val title = when (toolId) {
            "extract_pages" -> if (english) "Pages extracted" else "Páginas extraídas"
            "remove_pages" -> if (english) "Pages removed" else "Páginas removidas"
            "rotate" -> if (english) "Pages rotated" else "Páginas rodadas"
            "watermark" -> if (english) "Watermark added" else "Marca de água adicionada"
            "add_text" -> if (english) "Text added" else "Texto adicionado"
            "page_numbers" -> if (english) "Pages numbered" else "Páginas numeradas"
            "protect" -> if (english) "PDF protected" else "PDF protegido"
            "unlock" -> if (english) "PDF unlocked" else "PDF desbloqueado"
            "extract_text" -> if (english) "Text exported" else "Texto exportado"
            "to_images" -> if (english) "Page images exported" else "Imagens das páginas exportadas"
            else -> if (english) "PDF properties updated" else "Propriedades PDF atualizadas"
        }
        startOperation(
            title,
            if (english) "Applying the selected settings on this device" else "A aplicar as opções escolhidas neste dispositivo",
            mimeType = outputMime
        ) {
            val output = store.newOutputFile(file.nameWithoutExtension + "_" + toolId, outputExtension)
            withContext(Dispatchers.IO) {
                when (toolId) {
                    "extract_pages" -> PdfEngine.extractPages(context, file, options["range"].orEmpty(), output)
                    "remove_pages" -> PdfEngine.removePages(context, file, options["range"].orEmpty(), output)
                    "rotate" -> PdfEngine.rotatePages(context, file, options["degrees"]?.toIntOrNull() ?: 90, output)
                    "watermark" -> PdfEngine.addWatermark(context, file, options["text"].orEmpty(), output)
                    "add_text" -> PdfEngine.addTextOverlay(context, file, options["text"].orEmpty(), output)
                    "page_numbers" -> PdfEngine.addPageNumbers(context, file, options["start"]?.toIntOrNull() ?: 1, output)
                    "protect" -> PdfEngine.protectPdf(context, file, options["password"].orEmpty(), output)
                    "unlock" -> PdfEngine.unlockPdf(context, file, options["password"].orEmpty(), output)
                    "extract_text" -> PdfEngine.extractText(context, file, output)
                    "to_images" -> PdfEngine.exportPagesAsPngZip(context, file, options["dpi"]?.toIntOrNull() ?: 144, output)
                    "metadata" -> PdfEngine.updateMetadata(context, file, options["title"].orEmpty(), options["author"].orEmpty(), output)
                    else -> error("Unknown PDF tool")
                }
            }
            output
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val imported = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri -> runCatching { store.importPdf(uri) }.getOrNull() }
            }
            refresh()
            go(Screen.FILES)
            snackbar.showSnackbar(if (english) imported.size.toString() + " PDF file(s) imported" else imported.size.toString() + " PDF(s) importado(s)")
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
        if (selected.isNotEmpty()) {
            images = selected
            go(Screen.CREATE)
        }
    }

    var cameraOutput by remember { mutableStateOf<File?>(null) }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val output = cameraOutput
        if (saved && output != null && output.isFile && output.length() > 0L) {
            images = images + Uri.fromFile(output)
            go(Screen.CREATE)
        }
    }

    fun launchCamera() {
        runCatching {
            val directory = File(context.cacheDir, "camera").apply { mkdirs() }
            val output = File(directory, "scan_" + System.currentTimeMillis() + ".jpg")
            val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", output)
            cameraOutput = output
            cameraPicker.launch(uri)
        }.onFailure {
            scope.launch { snackbar.showSnackbar(it.message ?: if (english) "Camera could not be opened" else "Não foi possível abrir a câmara") }
        }
    }

    LaunchedEffect(initialPdf) {
        if (initialPdf != null) {
            val imported = withContext(Dispatchers.IO) { runCatching { store.importPdf(initialPdf) }.getOrNull() }
            if (imported != null) {
                refresh()
                selectedFile = imported
                store.recordOpened(imported)
                prefs.edit().putBoolean("onboarding_done", true).apply()
                go(Screen.READER)
            }
        }
    }

    BackHandler(enabled = (screen != Screen.HOME && screen != Screen.ONBOARDING) || drawer.isOpen || showQuick) {
        when {
            showQuick -> showQuick = false
            drawer.isOpen -> scope.launch { drawer.close() }
            else -> go(Screen.HOME)
        }
    }

    PDFMaxTheme(darkTheme = dark) {
        ModalNavigationDrawer(
            drawerState = drawer,
            scrimColor = Color(0x99020A18),
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(306.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surface
                ) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PdfMaxLogo(54)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("PDF Max", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text(if (english) "PDF Editor & Reader" else "Editor e leitor de PDF", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        DrawerItem(Icons.Default.Home, if (english) "Home" else "Início", screen == Screen.HOME) { go(Screen.HOME) }
                        DrawerItem(Icons.Default.Folder, if (english) "My Files" else "Meus Arquivos", screen == Screen.FILES) { go(Screen.FILES) }
                        DrawerItem(Icons.Default.Build, if (english) "Tools" else "Ferramentas", screen == Screen.TOOLS) { go(Screen.TOOLS) }
                        DrawerItem(Icons.Default.Star, if (english) "Favorites" else "Favoritos", screen == Screen.FAVORITES) { go(Screen.FAVORITES) }
                        DrawerItem(Icons.Default.History, if (english) "Recent" else "Recentes", screen == Screen.RECENT) { go(Screen.RECENT) }
                        DrawerItem(Icons.Default.DeleteOutline, if (english) "Trash" else "Lixo", screen == Screen.TRASH) { go(Screen.TRASH) }
                        HorizontalDivider(Modifier.padding(vertical = 14.dp))
                        DrawerItem(Icons.Default.Settings, if (english) "Settings" else "Configurações", screen == Screen.SETTINGS) { go(Screen.SETTINGS) }
                        DrawerItem(Icons.Default.HelpOutline, if (english) "Help & Support" else "Ajuda e suporte", false) {
                            scope.launch { snackbar.showSnackbar(if (english) "Choose a tool to see its instructions." else "Escolhe uma ferramenta para ver as instruções.") }
                            scope.launch { drawer.close() }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(if (english) "LANGUAGE" else "IDIOMA", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !english, onClick = { english = false; prefs.edit().putBoolean("english", false).apply() }, label = { Text("PT  Português") })
                            FilterChip(selected = english, onClick = { english = true; prefs.edit().putBoolean("english", true).apply() }, label = { Text("EN  English") })
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("PDF Max · 2.0", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally), fontSize = 11.sp)
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (screen !in setOf(Screen.ONBOARDING, Screen.READER, Screen.PROCESSING, Screen.RESULT, Screen.WORKSPACE, Screen.MERGE, Screen.CREATE, Screen.COMPRESS, Screen.TRASH)) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            NavigationBarItem(screen == Screen.HOME, { go(Screen.HOME) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text(if (english) "Home" else "Início") })
                            NavigationBarItem(screen == Screen.FILES, { go(Screen.FILES) }, icon = { Icon(Icons.Default.FolderOpen, null) }, label = { Text(if (english) "Files" else "Ficheiros") })
                            NavigationBarItem(false, { showQuick = true }, icon = {
                                Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(33.dp))
                            }, label = { Text(if (english) "Plus" else "Criar") })
                            NavigationBarItem(screen == Screen.FAVORITES, { go(Screen.FAVORITES) }, icon = { Icon(Icons.Default.Star, null) }, label = { Text(if (english) "Favorites" else "Favoritos") })
                            NavigationBarItem(false, { scope.launch { drawer.open() } }, icon = { Icon(Icons.Default.MoreHoriz, null) }, label = { Text(if (english) "More" else "Mais") })
                        }
                    }
                }
            ) { padding ->
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    transitionSpec = {
                        (fadeIn() + slideInHorizontally { it / 18 }) togetherWith
                            (fadeOut() + slideOutHorizontally { -it / 18 })
                    },
                    label = "PDF Max screen transition"
                ) { current ->
                    when (current) {
                        Screen.ONBOARDING -> WelcomeScreen(
                            english = english,
                            page = welcomePage,
                            onPage = { welcomePage = it },
                            onSkip = {
                                prefs.edit().putBoolean("onboarding_done", true).apply()
                                go(Screen.HOME)
                            },
                            onStart = {
                                prefs.edit().putBoolean("onboarding_done", true).apply()
                                go(Screen.HOME)
                            }
                        )
                        Screen.HOME -> HomeScreen(
                            english = english,
                            files = files,
                            favorites = favorites,
                            onMenu = { scope.launch { drawer.open() } },
                            onCreate = { images = emptyList(); go(Screen.CREATE) },
                            onCompress = { go(Screen.COMPRESS) },
                            onMerge = { go(Screen.MERGE) },
                            onTools = { go(Screen.TOOLS) },
                            onFiles = { go(Screen.FILES) },
                            onOpen = ::openPdf,
                            onFavorite = ::toggleFavorite
                        )
                        Screen.FILES, Screen.FAVORITES, Screen.RECENT -> {
                            val visible = when (current) {
                                Screen.FAVORITES -> files.filter { favorites.contains(it.file.absolutePath) }
                                Screen.RECENT -> recentPaths.mapNotNull { path -> files.firstOrNull { it.file.absolutePath == path } }
                                else -> files
                            }
                            LibraryScreen(
                                english = english,
                                title = when (current) {
                                    Screen.FAVORITES -> if (english) "Favorites" else "Favoritos"
                                    Screen.RECENT -> if (english) "Recent files" else "Ficheiros recentes"
                                    else -> if (english) "My Files" else "Meus Arquivos"
                                },
                                files = visible,
                                favorites = favorites,
                                query = query,
                                onQuery = { query = it },
                                onMenu = { scope.launch { drawer.open() } },
                                onImport = { pdfPicker.launch(arrayOf("application/pdf")) },
                                onOpen = ::openPdf,
                                onFavorite = ::toggleFavorite
                            )
                        }
                        Screen.TOOLS -> ToolsCatalogScreen(
                            english = english,
                            onCreate = { images = emptyList(); go(Screen.CREATE) },
                            onCompress = { go(Screen.COMPRESS) },
                            onMerge = { go(Screen.MERGE) },
                            onScan = ::launchCamera,
                            onOpenTool = { activeToolId = it; go(Screen.WORKSPACE) },
                            onFiles = { go(Screen.FILES) }
                        )
                        Screen.CREATE -> CreateScreen(
                            english = english,
                            images = images,
                            onGallery = { imagePicker.launch("image/*") },
                            onCamera = ::launchCamera,
                            onScan = ::launchCamera,
                            onCreate = ::createPdf,
                            onRemove = { index -> images = images.filterIndexed { i, _ -> i != index } }
                        )
                        Screen.COMPRESS -> SelectPdfScreen(
                            english = english,
                            title = if (english) "Compress PDF" else "Comprimir PDF",
                            subtitle = if (english) "Choose a PDF. The result will show the real size difference." else "Escolhe um PDF. O resultado mostrará a diferença real de tamanho.",
                            files = files,
                            onSelect = ::compressPdf,
                            onImport = { pdfPicker.launch(arrayOf("application/pdf")) }
                        )
                        Screen.MERGE -> MergeScreen(
                            english = english,
                            files = files,
                            selected = mergeSelection,
                            onToggle = { path ->
                                mergeSelection = if (mergeSelection.contains(path)) mergeSelection - path else mergeSelection + path
                            },
                            onMove = { path, delta ->
                                val index = mergeSelection.indexOf(path)
                                val target = index + delta
                                if (index >= 0 && target in mergeSelection.indices) {
                                    val updated = mergeSelection.toMutableList()
                                    val value = updated.removeAt(index)
                                    updated.add(target, value)
                                    mergeSelection = updated
                                }
                            },
                            onImport = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onMerge = ::mergePdfs,
                            onBack = { go(Screen.HOME) }
                        )
                        Screen.READER -> selectedFile?.let { file ->
                            ReaderScreen(
                                english = english,
                                file = file,
                                favorite = favorites.contains(file.absolutePath),
                                onBack = { go(Screen.FILES) },
                                onShare = { shareFile(file) },
                                onFavorite = { toggleFavorite(file) },
                                onTools = { go(Screen.TOOLS) }
                            )
                        } ?: EmptyState(if (english) "No document selected" else "Nenhum documento selecionado", if (english) "Choose a PDF from My Files." else "Escolhe um PDF em Meus Arquivos.", if (english) "Open files" else "Abrir ficheiros") { go(Screen.FILES) }
                        Screen.WORKSPACE -> ToolWorkspaceScreen(
                            toolId = activeToolId,
                            english = english,
                            files = files,
                            onBack = { go(Screen.TOOLS) },
                            onImport = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onExecute = { file, options -> runAdvancedTool(activeToolId, file, options) }
                        )
                        Screen.SETTINGS -> SettingsScreen(
                            english = english,
                            dark = dark,
                            storage = store.storageUsedBytes(),
                            onLanguage = {
                                english = !english
                                prefs.edit().putBoolean("english", english).apply()
                            },
                            onTheme = {
                                dark = !dark
                                prefs.edit().putBoolean("dark_theme", dark).apply()
                            }
                        )
                        Screen.TRASH -> TrashScreen(
                            english = english,
                            items = trash,
                            onBack = { go(Screen.HOME) },
                            onRestore = { file -> store.restoreFromTrash(file); refresh() },
                            onDelete = { file -> store.permanentlyDelete(file); refresh() }
                        )
                        Screen.PROCESSING -> ProcessingScreen(english, operationTitle, operationDetail, gearAngle)
                        Screen.RESULT -> result?.let { item ->
                            ResultScreen(
                                english = english,
                                result = item,
                                onShare = { shareFile(item.file, item.mimeType) },
                                onWhatsapp = { shareFile(item.file, item.mimeType, true) },
                                onOpen = if (item.mimeType == "application/pdf") ({ selectedFile = item.file; store.recordOpened(item.file); refresh(); go(Screen.READER) }) else null,
                                onDone = { refresh(); go(Screen.FILES) }
                            )
                        } ?: EmptyState("PDF Max", "", if (english) "Home" else "Início") { go(Screen.HOME) }
                    }
                }
            }
        }

        if (showQuick) {
            ModalBottomSheet(onDismissRequest = { showQuick = false }, containerColor = MaterialTheme.colorScheme.surface) {
                Text(if (english) "Quick actions" else "Ações rápidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                QuickAction(Icons.Default.Compress, if (english) "Compress PDF" else "Comprimir PDF") { showQuick = false; go(Screen.COMPRESS) }
                QuickAction(Icons.Default.MergeType, if (english) "Merge PDFs" else "Juntar PDFs") { showQuick = false; go(Screen.MERGE) }
                QuickAction(Icons.Default.AddPhotoAlternate, if (english) "Photo to PDF" else "Fotografia para PDF") { showQuick = false; images = emptyList(); go(Screen.CREATE) }
                Spacer(Modifier.height(22.dp))
            }
        }

        if (navigationBusy) {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background.copy(alpha = 0.97f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(28.dp)) {
                    Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(68.dp).rotate(gearAngle))
                    Spacer(Modifier.height(18.dp))
                    Text(navigationLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("PDF Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
    }
}


@Composable
private fun WelcomeScreen(
    english: Boolean,
    page: Int,
    onPage: (Int) -> Unit,
    onSkip: () -> Unit,
    onStart: () -> Unit
) {
    val headings = if (english) listOf(
        "Your PDFs, all in one place",
        "Create, compress and combine",
        "Read, organise and protect"
    ) else listOf(
        "Os teus PDFs, num só lugar",
        "Cria, comprime e junta",
        "Lê, organiza e protege"
    )
    val descriptions = if (english) listOf(
        "A focused document workspace designed to make everyday PDF tasks feel simple.",
        "Turn photos into PDFs, reduce file size and bring documents together in the order you choose.",
        "Preview every page, keep favourite files close and use dedicated tools when you need them."
    ) else listOf(
        "Um espaço de documentos pensado para tornar as tarefas PDF do dia a dia mais simples.",
        "Transforma fotografias em PDF, reduz o tamanho e junta documentos pela ordem que escolheres.",
        "Pré-visualiza páginas, guarda favoritos e utiliza ferramentas próprias para cada tarefa."
    )
    val icons = listOf(Icons.Default.PictureAsPdf, Icons.Default.AutoAwesome, Icons.Default.FolderOpen)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PdfMaxLogo(43)
            Spacer(Modifier.width(10.dp))
            Text("PDF Max", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSkip) { Text(if (english) "Skip" else "Saltar") }
        }

        Spacer(Modifier.weight(1f))
        androidx.compose.animation.AnimatedContent(
            targetState = page.coerceIn(0, 2),
            transitionSpec = {
                (androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInHorizontally { it / 5 }) togetherWith
                    (androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutHorizontally { -it / 5 })
            },
            label = "Welcome slides"
        ) { current ->
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    Modifier.fillMaxWidth().height(300.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF142E62), Color(0xFF101C3E), Color(0xFF064D68))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(188.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF0ECDE5), Color(0xFF2877EA))),
                                RoundedCornerShape(42.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icons[current], null, tint = Color.White, modifier = Modifier.size(104.dp))
                    }
                    Text(
                        "PDF",
                        modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp),
                        color = Color(0xFF8AEFFF),
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        letterSpacing = 2.sp
                    )
                }
                Spacer(Modifier.height(35.dp))
                Text(headings[current], style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(descriptions[current], color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, lineHeight = 22.sp, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            repeat(3) { index ->
                Box(
                    Modifier.size(if (index == page) 11.dp else 9.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (index == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                        .clickable { onPage(index) }
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                if (page < 2) onPage(page + 1) else onStart()
            },
            modifier = Modifier.fillMaxWidth().height(62.dp),
            shape = RoundedCornerShape(18.dp)
        ) {
            Text(
                if (page < 2) (if (english) "Continue" else "Continuar")
                else (if (english) "Start Now" else "Começar agora"),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            if (english) "Free · no account required" else "Gratuito · sem necessidade de conta",
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun PdfMaxLogo(size: Int) {
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(17.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF0CCDE6), Color(0xFF2877EA)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.InsertDriveFile, null, tint = Color.White, modifier = Modifier.size((size * 0.56f).dp))
        Icon(Icons.Default.Check, null, tint = Color(0xFF07305D), modifier = Modifier.align(Alignment.BottomEnd).padding(7.dp).size(15.dp))
    }
}

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(title, fontWeight = FontWeight.SemiBold) },
        icon = { Icon(icon, null) },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp),
        shape = RoundedCornerShape(13.dp)
    )
}

@Composable
private fun ScreenHeader(title: String, english: Boolean, onMenu: (() -> Unit)? = null, onBack: (() -> Unit)? = null, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, if (english) "Back" else "Voltar") }
        else IconButton(onClick = { onMenu?.invoke() }) { Icon(Icons.Default.Menu, if (english) "Menu" else "Menu") }
        Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) IconButton(onClick = action) { Icon(Icons.Default.Add, if (english) "Add" else "Adicionar") }
    }
}

@Composable
private fun HomeScreen(
    english: Boolean,
    files: List<PdfDocumentItem>,
    favorites: Set<String>,
    onMenu: () -> Unit,
    onCreate: () -> Unit,
    onCompress: () -> Unit,
    onMerge: () -> Unit,
    onTools: () -> Unit,
    onFiles: () -> Unit,
    onOpen: (File) -> Unit,
    onFavorite: (File) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            PdfMaxLogo(46)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text("PDF Max", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (english) "PDF Editor & Reader" else "Editor e leitor de PDF", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, if (english) "Open menu" else "Abrir menu") }
        }
        Text(if (english) "What would you like to do?" else "O que gostarias de fazer?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(if (english) "Your documents, ready for the next step." else "Os teus documentos, prontos para o próximo passo.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            HomeActionCard(Icons.Default.Compress, if (english) "Compress PDF" else "Comprimir PDF", if (english) "Reduce file size" else "Reduzir tamanho", Modifier.weight(1f), onCompress)
            HomeActionCard(Icons.Default.MergeType, if (english) "Merge PDF" else "Juntar PDFs", if (english) "Combine files" else "Unir ficheiros", Modifier.weight(1f), onMerge)
            HomeActionCard(Icons.Default.AddPhotoAlternate, if (english) "Create PDF" else "Criar PDF", if (english) "From photos" else "De fotografias", Modifier.weight(1f), onCreate)
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (english) "Recent files" else "Ficheiros recentes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onFiles) { Text(if (english) "See all" else "Ver todos") }
        }
        if (files.isEmpty()) {
            EmptyCard(
                title = if (english) "Your library is ready" else "A tua biblioteca está pronta",
                detail = if (english) "Import a PDF or create one from photos to get started." else "Importa um PDF ou cria um a partir de fotografias para começar.",
                action = if (english) "Explore tools" else "Explorar ferramentas",
                onClick = onTools
            )
        } else {
            files.take(4).forEach { item ->
                DocumentRow(item, favorites.contains(item.file.absolutePath), onOpen = { onOpen(item.file) }, onFavorite = { onFavorite(item.file) })
            }
        }
        Spacer(Modifier.height(20.dp))
        Card(
            onClick = onTools,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(Modifier.fillMaxWidth().padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (english) "Explore all tools" else "Explorar todas as ferramentas", fontWeight = FontWeight.Bold)
                    Text(if (english) "A dedicated workspace for every operation" else "Um espaço próprio para cada operação", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeActionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.height(145.dp),
        shape = RoundedCornerShape(19.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxSize().padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(
                Modifier.size(45.dp).background(Brush.linearGradient(listOf(Color(0xFF10CDE3), Color(0xFF2877EA))), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 11.sp, textAlign = TextAlign.Center, maxLines = 2)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.sp, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

@Composable
private fun LibraryScreen(
    english: Boolean,
    title: String,
    files: List<PdfDocumentItem>,
    favorites: Set<String>,
    query: String,
    onQuery: (String) -> Unit,
    onMenu: () -> Unit,
    onImport: () -> Unit,
    onOpen: (File) -> Unit,
    onFavorite: (File) -> Unit
) {
    val visible = files.filter { it.name.contains(query, ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, english, onMenu = onMenu, action = onImport)
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text(if (english) "Search PDFs…" else "Pesquisar PDFs…") },
            shape = RoundedCornerShape(17.dp)
        )
        Spacer(Modifier.height(10.dp))
        StorageSummary(files, english)
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    if (english) "No PDFs found" else "Nenhum PDF encontrado",
                    if (english) "Import a document to add it to your library." else "Importa um documento para o adicionar à biblioteca.",
                    if (english) "Import PDF" else "Importar PDF",
                    onImport
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(13.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                items(visible, key = { it.file.absolutePath }) { item ->
                    DocumentTile(item, favorites.contains(item.file.absolutePath), onOpen = { onOpen(item.file) }, onFavorite = { onFavorite(item.file) })
                }
            }
        }
    }
}

@Composable
private fun StorageSummary(files: List<PdfDocumentItem>, english: Boolean) {
    val bytes = files.sumOf { it.sizeBytes }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (english) "PDF Max storage" else "Armazenamento PDF Max", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(formatBytes(bytes), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Text(if (english) "Space used by documents saved inside PDF Max" else "Espaço ocupado pelos documentos guardados no PDF Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun DocumentTile(item: PdfDocumentItem, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Card(
        onClick = onOpen,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Box(
                Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF7F9FC)),
                contentAlignment = Alignment.Center
            ) {
                PdfPagePreview(item.file, 0, Modifier.fillMaxSize().padding(8.dp))
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (favorite) Color(0xFFFFC857) else Color(0xFF61718D))
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(item.name, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(formatBytes(item.sizeBytes) + " · " + item.formattedDate, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DocumentRow(item: PdfDocumentItem, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 58.dp, height = 72.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF7F9FC)), contentAlignment = Alignment.Center) {
                PdfPagePreview(item.file, 0, Modifier.fillMaxSize().padding(4.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(formatBytes(item.sizeBytes) + " · " + item.formattedDate, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (favorite) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PdfPagePreview(file: File, pageIndex: Int, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, file.absolutePath, pageIndex) {
        value = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex, 680) }
    }
    if (image == null) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
    } else {
        Image(image!!, contentDescription = file.name, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

private fun renderPdfPage(file: File, pageIndex: Int, targetWidth: Int): ImageBitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) return@runCatching null
            renderer.openPage(pageIndex).use { page ->
                val targetHeight = (targetWidth.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(120)
                Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }.asImageBitmap()
            }
        }
    }
}.getOrNull()

@Composable
private fun CreateScreen(
    english: Boolean,
    images: List<Uri>,
    onGallery: () -> Unit,
    onCamera: () -> Unit,
    onScan: () -> Unit,
    onCreate: () -> Unit,
    onRemove: (Int) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp)) {
        ScreenHeader(if (english) "Create PDF from Photo" else "Criar PDF a partir de fotografia", english, onBack = null)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(15.dp)
        ) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF36C98F))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (english) "Document ready" else "Documento preparado", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (images.isEmpty()) (if (english) "Choose photos or capture a page" else "Escolhe fotografias ou captura uma página")
                        else if (english) images.size.toString() + " page(s) selected" else images.size.toString() + " página(s) selecionada(s)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
                if (images.isNotEmpty()) TextButton(onClick = { onRemove(-1) }) { Text(if (english) "Clear" else "Limpar") }
            }
        }
        Spacer(Modifier.height(15.dp))
        Box(
            Modifier.fillMaxWidth().height(230.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF061126)),
            contentAlignment = Alignment.Center
        ) {
            if (images.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.DocumentScanner, null, tint = Color(0xFF19C8FF), modifier = Modifier.size(60.dp))
                    Text(if (english) "Camera preview" else "Pré-visualização da câmara", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(if (english) "Captured pages will appear here" else "As páginas capturadas aparecerão aqui", color = Color(0xFFA5B6D3), fontSize = 11.sp)
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Collections, null, tint = Color(0xFF19C8FF), modifier = Modifier.size(42.dp))
                    Text(if (english) "Ready to create " + images.size + " page(s)" else "Pronto para criar " + images.size + " página(s)", color = Color.White, fontWeight = FontWeight.SemiBold)
                    Text(if (english) "Images are kept on this device" else "As imagens permanecem neste dispositivo", color = Color(0xFFA5B6D3), fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CaptureAction(Icons.Default.PhotoLibrary, if (english) "Gallery" else "Galeria", Modifier.weight(1f), onGallery)
            CaptureAction(Icons.Default.CameraAlt, if (english) "Camera" else "Câmara", Modifier.weight(1f), onCamera)
            CaptureAction(Icons.Default.DocumentScanner, if (english) "Scan" else "Digitalizar", Modifier.weight(1f), onScan)
        }
        if (images.isNotEmpty()) {
            Spacer(Modifier.height(17.dp))
            Text(if (english) "PAGE ORDER" else "ORDEM DAS PÁGINAS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            images.forEachIndexed { index, _ ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Image, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text((if (english) "Page " else "Página ") + (index + 1), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = { onRemove(index) }) { Icon(Icons.Default.Close, if (english) "Remove page" else "Remover página") }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate, enabled = images.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(59.dp), shape = RoundedCornerShape(17.dp)) {
            Text(if (english) "Create PDF" else "Criar PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Text(if (english) "The original photos are not modified." else "As fotografias originais não são modificadas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(10.dp))
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun CaptureAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(83.dp), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
            Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SelectPdfScreen(
    english: Boolean,
    title: String,
    subtitle: String,
    files: List<PdfDocumentItem>,
    onSelect: (File) -> Unit,
    onImport: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, english, onBack = null, action = onImport)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 17.dp))
        if (files.isEmpty()) {
            EmptyState(
                if (english) "No PDFs in your library" else "Não existem PDFs na biblioteca",
                if (english) "Import a PDF to get started." else "Importa um PDF para começar.",
                if (english) "Import PDF" else "Importar PDF",
                onImport
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(14.dp)) {
                items(files, key = { it.file.absolutePath }) { item ->
                    Card(
                        onClick = { onSelect(item.file) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(51.dp, 65.dp).clip(RoundedCornerShape(7.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                                PdfPagePreview(item.file, 0, Modifier.fillMaxSize().padding(4.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.formattedSize + " · " + item.formattedDate, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                            }
                            Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeScreen(
    english: Boolean,
    files: List<PdfDocumentItem>,
    selected: List<String>,
    onToggle: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onImport: () -> Unit,
    onMerge: () -> Unit,
    onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        ScreenHeader(if (english) "Merge PDF" else "Juntar PDFs", english, onBack = onBack, action = onImport)
        Text(
            if (english) selected.size.toString() + " files selected · choose at least two" else selected.size.toString() + " ficheiro(s) · escolhe pelo menos dois",
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(if (english) "Tap a file to add it. Reorder the selected files before merging." else "Toca num ficheiro para o adicionar. Reorganiza os selecionados antes de juntar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        if (selected.isNotEmpty()) {
            LazyColumn(Modifier.heightIn(max = 230.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(selected, key = { it }) { path ->
                    val item = files.firstOrNull { it.file.absolutePath == path }
                    if (item != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(item.formattedSize, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onMove(path, -1) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                                IconButton(onClick = { onMove(path, 1) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                                IconButton(onClick = { onToggle(path) }) { Icon(Icons.Default.Close, null) }
                            }
                        }
                    }
                }
            }
        }
        Text(if (english) "AVAILABLE DOCUMENTS" else "DOCUMENTOS DISPONÍVEIS", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 9.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(files, key = { it.file.absolutePath }) { item ->
                val checked = selected.contains(item.file.absolutePath)
                Row(
                    Modifier.fillMaxWidth().clickable { onToggle(item.file.absolutePath) }.padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked, { onToggle(item.file.absolutePath) })
                    Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(item.formattedSize, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            }
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text(if (english) "Add more files" else "Adicionar mais ficheiros") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onMerge, enabled = selected.size >= 2, modifier = Modifier.fillMaxWidth().height(57.dp), shape = RoundedCornerShape(16.dp)) {
            Text(if (english) "Merge now" else "Juntar agora", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Text(if (english) "Files are combined locally. Originals remain untouched." else "Os ficheiros são unidos localmente. Os originais permanecem intactos.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
    }
}

@Composable
private fun ReaderScreen(
    english: Boolean,
    file: File,
    favorite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onFavorite: () -> Unit,
    onTools: () -> Unit
) {
    val context = LocalContext.current
    val pageCount by produceState<Int?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { runCatching { PdfEngine.pageCount(context, file) }.getOrNull() }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Column(Modifier.weight(1f)) {
                Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (english) (pageCount?.toString() ?: "…") + " pages" else (pageCount?.toString() ?: "…") + " páginas", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
            }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (favorite) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, null) }
            IconButton(onClick = onTools) { Icon(Icons.Default.Build, if (english) "Edit tools" else "Ferramentas de edição") }
        }
        if (pageCount == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (pageCount == 0) {
            EmptyState(if (english) "No pages found" else "Nenhuma página encontrada", if (english) "This document could not be rendered." else "Não foi possível apresentar este documento.", if (english) "Back" else "Voltar", onBack)
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp)
            ) {
                items(pageCount ?: 0) { index ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            PdfPagePreview(file, index, Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 690.dp).padding(8.dp))
                        }
                        Text((if (english) "Page " else "Página ") + (index + 1) + " / " + pageCount, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp))
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Button(onClick = onTools, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Default.Build, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (english) "Open PDF tools" else "Abrir ferramentas PDF")
                }
                Text(if (english) "Use the dedicated tools for page rotation, text overlays, watermarks, numbering and security." else "Usa as ferramentas próprias para rodar páginas, adicionar texto, marcas de água, numeração e proteção.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp))
            }
        }
    }
}

@Composable
private fun ReaderTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null)
            Text(label, fontSize = 9.sp)
        }
    }
}

@Composable
private fun TrashScreen(
    english: Boolean,
    items: List<PdfDocumentItem>,
    onBack: () -> Unit,
    onRestore: (File) -> Unit,
    onDelete: (File) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(if (english) "Trash" else "Lixo", english, onBack = onBack)
        if (items.isEmpty()) {
            EmptyState(if (english) "Trash is empty" else "O lixo está vazio", if (english) "Deleted PDFs will appear here." else "Os PDFs removidos aparecerão aqui.", if (english) "Back" else "Voltar", onBack)
        } else {
            LazyColumn(contentPadding = PaddingValues(14.dp)) {
                items(items, key = { it.file.absolutePath }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(item.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.formattedSize, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onRestore(item.file) }) { Icon(Icons.Default.Restore, null); Text(if (english) "Restore" else "Restaurar") }
                                TextButton(onClick = { onDelete(item.file) }) { Icon(Icons.Default.DeleteForever, null); Text(if (english) "Delete forever" else "Eliminar") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProcessingScreen(english: Boolean, title: String, detail: String, gearAngle: Float) {
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(25.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(26.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Settings, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp).rotate(gearAngle))
                Spacer(Modifier.height(18.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(11.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(13.dp))
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 12.sp)
                Text(if (english) "Processing locally. Keep PDF Max open." else "Processamento local. Mantém o PDF Max aberto.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 15.dp))
            }
        }
    }
}

@Composable
private fun ResultScreen(
    english: Boolean,
    result: ResultFile,
    onShare: () -> Unit,
    onWhatsapp: () -> Unit,
    onOpen: (() -> Unit)?,
    onDone: () -> Unit
) {
    val after = result.file.length()
    val saved = (result.beforeBytes ?: after) - after
    val percent = if (result.beforeBytes != null && result.beforeBytes > 0L) ((saved.toDouble() / result.beforeBytes) * 100).roundToInt() else 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Box(Modifier.size(78.dp).background(Brush.linearGradient(listOf(Color(0xFF13C8DF), Color(0xFF2678E9))), RoundedCornerShape(25.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(result.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(result.file.name, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 11.sp)
        Spacer(Modifier.height(22.dp))
        if (result.beforeBytes != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(19.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text(if (english) "Before" else "Antes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatBytes(result.beforeBytes), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (english) "After" else "Depois", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatBytes(after), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    percent > 0 -> if (english) "Saved " + percent + "%" else "Redução real: " + percent + "%"
                    saved == 0L -> if (english) "No size change" else "O tamanho não mudou"
                    else -> if (english) "The output is larger by " + formatBytes(-saved) else "O ficheiro final ficou maior em " + formatBytes(-saved)
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        } else {
            Text(formatBytes(after), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onWhatsapp,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF20C96B), contentColor = Color.White)
        ) {
            Icon(Icons.Default.Share, null)
            Spacer(Modifier.width(9.dp))
            Text(if (english) "Share on WhatsApp" else "Partilhar no WhatsApp", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth().height(51.dp)) { Text(if (english) "More sharing options" else "Mais opções de partilha") }
        if (onOpen != null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(51.dp)) { Text(if (english) "Open document" else "Abrir documento") }
        }
        TextButton(onClick = onDone) { Text(if (english) "Done" else "Concluído") }
        Text(if (english) "The original file remains unchanged." else "O ficheiro original permanece intacto.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun SettingsScreen(
    english: Boolean,
    dark: Boolean,
    storage: Long,
    onLanguage: () -> Unit,
    onTheme: () -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        ScreenHeader(if (english) "Settings" else "Configurações", english)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PdfMaxLogo(61)
            Spacer(Modifier.width(14.dp))
            Column {
                Text("PDF Max", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (english) "PDF Editor & Reader" else "Editor e leitor de PDF", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        SettingSection(if (english) "GENERAL" else "GERAL")
        SettingRow(Icons.Default.Language, if (english) "Language" else "Idioma", if (english) "English" else "Português", onLanguage)
        SettingRow(Icons.Default.DarkMode, if (english) "Theme" else "Tema", if (dark) (if (english) "Dark" else "Escuro") else (if (english) "Light" else "Claro"), onTheme)
        SettingSection(if (english) "STORAGE" else "ARMAZENAMENTO")
        SettingRow(Icons.Default.Storage, if (english) "PDF Max files" else "Ficheiros PDF Max", formatBytes(storage), {})
        SettingSection(if (english) "PRIVACY & SUPPORT" else "PRIVACIDADE E SUPORTE")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(15.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (english) "Documents are processed on this device. PDF Max opens files only when you choose them and shares them only when you request it."
                    else "Os documentos são processados neste dispositivo. O PDF Max abre ficheiros quando os escolhes e só os partilha quando solicitas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("PDF Max · 2.0", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally), fontSize = 11.sp)
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(top = 24.dp, bottom = 7.dp))
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
}

@Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title, fontWeight = FontWeight.SemiBold) },
        leadingContent = { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun EmptyCard(title: String, detail: String, action: String, onClick: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(9.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(11.dp))
            Button(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String, action: String, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f MB", value / (1024f * 1024f))
    value >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", value / 1024f)
    else -> "$value B"
}
