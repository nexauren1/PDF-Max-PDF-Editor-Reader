package com.nexauren.pdfmax.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.nexauren.pdfmax.data.DocumentStore
import com.nexauren.pdfmax.data.PdfDocumentItem
import com.nexauren.pdfmax.data.PdfEngine
import com.nexauren.pdfmax.ui.theme.PDFMaxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

private enum class Screen { HOME, FILES, TOOLS, FAVORITES, SETTINGS, CREATE, COMPRESS, MERGE, READER, PROCESSING, RESULT }
private data class ResultFile(val file: File, val title: String, val beforeBytes: Long? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDFMaxApp(initialPdf: Uri? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { DocumentStore(context) }
    val prefs = remember(context) { context.getSharedPreferences("pdfmax_v2", Context.MODE_PRIVATE) }
    var dark by rememberSaveable { mutableStateOf(prefs.getBoolean("dark_theme", true)) }
    var english by rememberSaveable { mutableStateOf(prefs.getBoolean("english", false)) }
    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var files by remember { mutableStateOf(store.listDocuments()) }
    var favorites by remember { mutableStateOf(store.favoritePaths()) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var mergeSelection by remember { mutableStateOf(listOf<String>()) }
    var images by remember { mutableStateOf(listOf<Uri>()) }
    var result by remember { mutableStateOf<ResultFile?>(null) }
    var operationTitle by remember { mutableStateOf("") }
    var operationDetail by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var showQuick by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val snackbar = remember { SnackbarHostState() }

    fun refresh() { files = store.listDocuments(); favorites = store.favoritePaths() }
    fun go(to: Screen) { screen = to; scope.launch { drawer.close() } }
    fun open(file: File) { selectedFile = file; go(Screen.READER) }
    fun toggleFavorite(file: File) { store.toggleFavorite(file); refresh() }
    fun share(file: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, if (english) "Share PDF" else "Partilhar PDF"))
        }.onFailure { scope.launch { snackbar.showSnackbar(it.message ?: "Erro ao partilhar") } }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            val count = withContext(Dispatchers.IO) { uris.count { uri -> runCatching { store.importPdf(uri) }.isSuccess } }
            refresh(); go(Screen.FILES)
            snackbar.showSnackbar(if (english) "$count PDF file(s) imported" else "$count PDF(s) importado(s)")
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { selected ->
        if (selected.isNotEmpty()) { images = selected; go(Screen.CREATE) }
    }
    val cameraPicker = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) scope.launch {
            val uri = withContext(Dispatchers.IO) {
                val file = File(context.cacheDir, "scan_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) }
                Uri.fromFile(file)
            }
            images = listOf(uri); go(Screen.CREATE)
        }
    }

    LaunchedEffect(initialPdf) {
        if (initialPdf != null) {
            val imported = withContext(Dispatchers.IO) { runCatching { store.importPdf(initialPdf) }.getOrNull() }
            if (imported != null) { refresh(); selectedFile = imported; screen = Screen.READER }
        }
    }

    fun startOperation(title: String, detail: String, work: suspend () -> ResultFile) {
        operationTitle = title; operationDetail = detail; go(Screen.PROCESSING)
        scope.launch {
            runCatching { work() }
                .onSuccess { result = it; refresh(); go(Screen.RESULT) }
                .onFailure {
                    go(Screen.HOME)
                    snackbar.showSnackbar(it.message ?: if (english) "PDF operation failed" else "A operação falhou")
                }
        }
    }
    fun createPdf() {
        if (images.isEmpty()) return
        startOperation(
            if (english) "Creating PDF" else "A criar PDF",
            if (english) "Preparing pages from your images" else "A preparar as páginas das fotografias"
        ) {
            val output = store.newPdfFile("Photo_to_PDF")
            withContext(Dispatchers.IO) { PdfEngine.createPdfFromImages(context, images, output) }
            ResultFile(output, if (english) "PDF created" else "PDF criado")
        }
    }
    fun compressPdf(file: File) {
        val before = file.length()
        startOperation(
            if (english) "Optimizing PDF" else "A otimizar PDF",
            if (english) "Rewriting document data" else "A reconstruir os dados do documento"
        ) {
            val output = store.newPdfFile(file.nameWithoutExtension + "_optimized")
            withContext(Dispatchers.IO) { PdfEngine.rewritePdf(context, file, output) }
            ResultFile(output, if (english) "Optimization complete" else "Otimização concluída", before)
        }
    }
    fun mergePdfs() {
        val selected = mergeSelection.mapNotNull { path -> files.firstOrNull { it.file.absolutePath == path }?.file }
        if (selected.size < 2) return
        startOperation(
            if (english) "Merging PDFs" else "A juntar PDFs",
            if (english) "Combining ${selected.size} selected documents" else "A combinar ${selected.size} documentos"
        ) {
            val output = store.newPdfFile("Merged_document")
            withContext(Dispatchers.IO) { PdfEngine.mergePdfs(context, selected, output) }
            ResultFile(output, if (english) "PDFs merged" else "PDFs unidos", selected.sumOf { it.length() })
        }
    }

    PDFMaxTheme(darkTheme = dark) {
        ModalNavigationDrawer(
            drawerState = drawer,
            scrimColor = Color(0x99020A18),
            drawerContent = {
                ModalDrawerSheet(Modifier.width(300.dp)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Logo(54); Spacer(Modifier.width(12.dp))
                            Column { Text("PDF Max", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text(if (english) "PDF Editor & Reader" else "Editor e leitor de PDF", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(Modifier.height(22.dp))
                        DrawerLink(Icons.Default.Home, if (english) "Home" else "Início") { go(Screen.HOME) }
                        DrawerLink(Icons.Default.Folder, if (english) "My Files" else "Meus Arquivos") { go(Screen.FILES) }
                        DrawerLink(Icons.Default.Build, if (english) "Tools" else "Ferramentas") { go(Screen.TOOLS) }
                        DrawerLink(Icons.Default.Star, if (english) "Favorites" else "Favoritos") { go(Screen.FAVORITES) }
                        DrawerLink(Icons.Default.Settings, if (english) "Settings" else "Configurações") { go(Screen.SETTINGS) }
                        HorizontalDivider(Modifier.padding(vertical = 14.dp))
                        Text(if (english) "Language" else "Idioma", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { english = !english; prefs.edit().putBoolean("english", english).apply() }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (english) "English · Português" else "Português · English")
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("v2.0.0 · PDF Max", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbar) },
                bottomBar = {
                    if (screen !in setOf(Screen.READER, Screen.PROCESSING, Screen.RESULT)) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            NavigationBarItem(screen == Screen.HOME, { go(Screen.HOME) }, icon = { Icon(Icons.Default.Home, null) }, label = { Text(if (english) "Home" else "Início") })
                            NavigationBarItem(screen == Screen.FILES, { go(Screen.FILES) }, icon = { Icon(Icons.Default.Folder, null) }, label = { Text(if (english) "Files" else "Ficheiros") })
                            NavigationBarItem(false, { showQuick = true }, icon = { Icon(Icons.Default.AddCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(31.dp)) }, label = { Text(if (english) "Create" else "Criar") })
                            NavigationBarItem(screen == Screen.FAVORITES, { go(Screen.FAVORITES) }, icon = { Icon(Icons.Default.Star, null) }, label = { Text(if (english) "Favorites" else "Favoritos") })
                            NavigationBarItem(false, { scope.launch { drawer.open() } }, icon = { Icon(Icons.Default.MoreHoriz, null) }, label = { Text(if (english) "More" else "Mais") })
                        }
                    }
                }
            ) { padding ->
                AnimatedContent(
                    targetState = screen,
                    modifier = Modifier.fillMaxSize().padding(padding),
                    transitionSpec = { (fadeIn() + slideInHorizontally { it / 22 }) togetherWith (fadeOut() + slideOutHorizontally { -it / 22 }) },
                    label = "PDF Max screen"
                ) { current ->
                    when (current) {
                        Screen.HOME -> HomeScreen(
                            english, files, favorites,
                            onMenu = { scope.launch { drawer.open() } },
                            onCreate = { images = emptyList(); go(Screen.CREATE) },
                            onCompress = { go(Screen.COMPRESS) },
                            onMerge = { go(Screen.MERGE) },
                            onTools = { go(Screen.TOOLS) },
                            onFiles = { go(Screen.FILES) },
                            onOpen = ::open,
                            onFavorite = ::toggleFavorite
                        )
                        Screen.FILES, Screen.FAVORITES -> FilesScreen(
                            english, files.filter { current != Screen.FAVORITES || favorites.contains(it.file.absolutePath) },
                            query, { query = it },
                            onMenu = { scope.launch { drawer.open() } },
                            onImport = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onOpen = ::open,
                            onFavorite = ::toggleFavorite,
                            favoritePaths = favorites
                        )
                        Screen.TOOLS -> ToolsScreen(
                            english,
                            onCreate = { images = emptyList(); go(Screen.CREATE) },
                            onCompress = { go(Screen.COMPRESS) },
                            onMerge = { go(Screen.MERGE) },
                            onScan = { cameraPicker.launch(null) },
                            onFiles = { go(Screen.FILES) }
                        )
                        Screen.CREATE -> CreateScreen(
                            english, images.size,
                            onGallery = { imagePicker.launch("image/*") },
                            onCamera = { cameraPicker.launch(null) },
                            onCreate = ::createPdf,
                            onClear = { images = emptyList() }
                        )
                        Screen.COMPRESS -> SelectPdfScreen(
                            english, if (english) "Compress PDF" else "Comprimir PDF",
                            if (english) "Choose a document to optimize" else "Escolhe um documento para otimizar",
                            files, onSelect = ::compressPdf, onImport = { pdfPicker.launch(arrayOf("application/pdf")) }
                        )
                        Screen.MERGE -> MergeScreen(
                            english, files, mergeSelection,
                            onToggle = { path -> mergeSelection = if (mergeSelection.contains(path)) mergeSelection - path else mergeSelection + path },
                            onMove = { path, delta ->
                                val index = mergeSelection.indexOf(path); val target = index + delta
                                if (index >= 0 && target in mergeSelection.indices) {
                                    val list = mergeSelection.toMutableList(); val value = list.removeAt(index); list.add(target, value); mergeSelection = list
                                }
                            },
                            onImport = { pdfPicker.launch(arrayOf("application/pdf")) },
                            onMerge = ::mergePdfs
                        )
                        Screen.READER -> selectedFile?.let { file ->
                            ReaderScreen(english, file, favorites.contains(file.absolutePath), onBack = { go(Screen.FILES) }, onShare = { share(file) }, onFavorite = { toggleFavorite(file) }, onAction = { label ->
                                scope.launch { snackbar.showSnackbar(if (english) "$label editing is planned for the next update" else "A ferramenta $label será ligada ao editor numa próxima atualização") }
                            })
                        } ?: EmptyState(if (english) "No PDF selected" else "Nenhum PDF selecionado", if (english) "Choose a document from My Files." else "Escolhe um documento em Meus Arquivos.", if (english) "Open files" else "Abrir ficheiros") { go(Screen.FILES) }
                        Screen.SETTINGS -> SettingsScreen(
                            english, dark, store.storageUsedBytes(),
                            onLanguage = { english = !english; prefs.edit().putBoolean("english", english).apply() },
                            onTheme = { dark = !dark; prefs.edit().putBoolean("dark_theme", dark).apply() }
                        )
                        Screen.PROCESSING -> ProcessingScreen(english, operationTitle, operationDetail)
                        Screen.RESULT -> result?.let { item ->
                            ResultScreen(english, item, onShare = { share(item.file) }, onOpen = { selectedFile = item.file; go(Screen.READER) }, onDone = { refresh(); go(Screen.FILES) })
                        } ?: EmptyState("PDF Max", " ", if (english) "Home" else "Início") { go(Screen.HOME) }
                    }
                }
            }
        }
        if (showQuick) {
            ModalBottomSheet(onDismissRequest = { showQuick = false }) {
                Text(if (english) "Quick actions" else "Ações rápidas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp))
                QuickLink(Icons.Default.Compress, if (english) "Compress PDF" else "Comprimir PDF") { showQuick = false; go(Screen.COMPRESS) }
                QuickLink(Icons.Default.MergeType, if (english) "Merge PDFs" else "Juntar PDFs") { showQuick = false; go(Screen.MERGE) }
                QuickLink(Icons.Default.AddPhotoAlternate, if (english) "Photo to PDF" else "Fotografia para PDF") { showQuick = false; imagePicker.launch("image/*") }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Logo(size: Int) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(16.dp)).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF10CDE3), Color(0xFF2674E8)))), contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size((size * 0.58f).dp))
    }
}

@Composable
private fun DrawerLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(title, fontWeight = FontWeight.SemiBold) },
        icon = { Icon(icon, null) },
        selected = false,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}

@Composable
private fun Header(title: String, english: Boolean, onMenu: () -> Unit, action: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Default.Menu, null) }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (action != null) IconButton(onClick = action) { Icon(Icons.Default.Add, if (english) "Add" else "Adicionar") }
    }
}

@Composable
private fun HomeScreen(
    english: Boolean, files: List<PdfDocumentItem>, favorites: Set<String>,
    onMenu: () -> Unit, onCreate: () -> Unit, onCompress: () -> Unit, onMerge: () -> Unit,
    onTools: () -> Unit, onFiles: () -> Unit, onOpen: (File) -> Unit, onFavorite: (File) -> Unit
) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Header("PDF Max", english, onMenu)
        Spacer(Modifier.height(18.dp))
        Text(if (english) "What would you like to do?" else "O que gostarias de fazer?", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            QuickCard(Icons.Default.Compress, if (english) "Compress PDF" else "Comprimir PDF", if (english) "Reduce size" else "Reduzir tamanho", Modifier.weight(1f), onCompress)
            QuickCard(Icons.Default.MergeType, if (english) "Merge PDF" else "Juntar PDF", if (english) "Combine files" else "Unir ficheiros", Modifier.weight(1f), onMerge)
            QuickCard(Icons.Default.AddPhotoAlternate, if (english) "Create PDF" else "Criar PDF", if (english) "From photos" else "De fotos", Modifier.weight(1f), onCreate)
        }
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (english) "Recent files" else "Ficheiros recentes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onFiles) { Text(if (english) "See all" else "Ver todos") }
        }
        if (files.isEmpty()) {
            EmptyCard(if (english) "Your PDFs will appear here" else "Os teus PDFs aparecerão aqui", if (english) "Import a PDF or create one from photos." else "Importa um PDF ou cria um a partir de fotografias.", if (english) "Open tools" else "Abrir ferramentas", onTools)
        } else {
            files.take(4).forEach { item ->
                DocumentRow(item, favorites.contains(item.file.absolutePath), { onOpen(item.file) }, { onFavorite(item.file) })
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (english) "More tools" else "Mais ferramentas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onTools) { Text(if (english) "View all" else "Ver todas") }
        }
        Text(if (english) "A complete workspace for everyday PDF tasks." else "Um espaço de trabalho para as tarefas PDF do dia a dia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuickCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(142.dp), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxSize().padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(9.dp))
            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2)
            Text(subtitle, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
private fun FilesScreen(
    english: Boolean, files: List<PdfDocumentItem>, query: String, onQuery: (String) -> Unit,
    onMenu: () -> Unit, onImport: () -> Unit, onOpen: (File) -> Unit, onFavorite: (File) -> Unit, favoritePaths: Set<String>
) {
    val visible = files.filter { it.name.contains(query, true) }
    Column(Modifier.fillMaxSize()) {
        Header(if (english) "My Files" else "Meus Arquivos", english, onMenu, onImport)
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) },
            placeholder = { Text(if (english) "Search PDFs..." else "Pesquisar PDFs...") },
            shape = RoundedCornerShape(18.dp)
        )
        Spacer(Modifier.height(10.dp))
        StorageSummary(files)
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(if (english) "No PDFs found" else "Nenhum PDF encontrado", if (english) "Import a document to add it to your library." else "Importa um documento para o adicionar à biblioteca.", if (english) "Import PDF" else "Importar PDF", onImport)
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2), contentPadding = PaddingValues(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visible, key = { it.file.absolutePath }) { item ->
                    DocumentCard(item, favoritePaths.contains(item.file.absolutePath), { onOpen(item.file) }, { onFavorite(item.file) })
                }
            }
        }
    }
}

@Composable
private fun StorageSummary(files: List<PdfDocumentItem>) {
    val bytes = files.sumOf { it.sizeBytes }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(17.dp)) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (files.size == 1) "1 PDF" else "${files.size} PDFs", fontWeight = FontWeight.Bold)
                Text("${formatBytes(bytes)} · PDF Max", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun DocumentCard(item: PdfDocumentItem, favorite: Boolean, onClick: () -> Unit, onFavorite: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(9.dp)) {
            Box(Modifier.fillMaxWidth().height(145.dp).clip(RoundedCornerShape(11.dp)).background(Color.White)) {
                PagePreview(item.file, Modifier.fillMaxSize())
                IconButton(onClick = onFavorite, modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(32.dp).background(Color(0xCC172746), CircleShape)) {
                    Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (favorite) Color(0xFFFFD65A) else Color.White, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(item.name, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("${item.formattedSize} · ${item.formattedDate}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DocumentRow(item: PdfDocumentItem, favorite: Boolean, onOpen: () -> Unit, onFavorite: () -> Unit) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(17.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(60.dp).clip(RoundedCornerShape(9.dp)).background(Color.White)) { PagePreview(item.file, Modifier.fillMaxSize()) }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text("${item.formattedDate} · ${item.formattedSize}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null, tint = if (favorite) Color(0xFFFFD65A) else MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PagePreview(file: File, modifier: Modifier = Modifier) {
    val image by produceState<ImageBitmap?>(null, file.absolutePath) {
        value = withContext(Dispatchers.IO) { renderPage(file, 0, 420) }
    }
    if (image == null) Box(modifier, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFF2674E8), modifier = Modifier.size(38.dp))
    } else Image(image!!, file.name, modifier, contentScale = androidx.compose.ui.layout.ContentScale.Fit)
}

private fun renderPage(file: File, index: Int, width: Int): ImageBitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (index >= renderer.pageCount) return@runCatching null
            renderer.openPage(index).use { page ->
                val height = (width.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(160)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }.asImageBitmap()
            }
        }
    }
}.getOrNull()

@Composable
private fun ToolsScreen(english: Boolean, onCreate: () -> Unit, onCompress: () -> Unit, onMerge: () -> Unit, onScan: () -> Unit, onFiles: () -> Unit) {
    var category by rememberSaveable { mutableIntStateOf(0) }
    val labels = if (english) listOf("All", "Edit", "Organize", "Convert") else listOf("Todos", "Editar", "Organizar", "Converter")
    val tools = listOf(
        Triple(Icons.Default.AddPhotoAlternate, if (english) "Create PDF" else "Criar PDF", 3),
        Triple(Icons.Default.Edit, if (english) "Edit PDF" else "Editar PDF", 1),
        Triple(Icons.Default.Compress, if (english) "Compress PDF" else "Comprimir PDF", 2),
        Triple(Icons.Default.MergeType, if (english) "Merge PDF" else "Juntar PDF", 2),
        Triple(Icons.Default.DocumentScanner, if (english) "Scan document" else "Digitalizar documento", 3),
        Triple(Icons.Default.PictureAsPdf, if (english) "Read PDF" else "Ler PDF", 1)
    )
    val visible = tools.filter { category == 0 || it.third == category }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Header(if (english) "Tools & Features" else "Ferramentas", english, {})
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            labels.forEachIndexed { index, label -> FilterChip(category == index, { category = index }, label = { Text(label) }) }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Fixed(2), contentPadding = PaddingValues(bottom = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(visible) { tool ->
                val click = when (tool.second) {
                    "Criar PDF", "Create PDF" -> onCreate
                    "Comprimir PDF", "Compress PDF" -> onCompress
                    "Juntar PDF", "Merge PDF" -> onMerge
                    "Digitalizar documento", "Scan document" -> onScan
                    else -> onFiles
                }
                Card(onClick = click, modifier = Modifier.height(145.dp), shape = RoundedCornerShape(19.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(tool.first, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                        Spacer(Modifier.height(12.dp))
                        Text(tool.second, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateScreen(english: Boolean, count: Int, onGallery: () -> Unit, onCamera: () -> Unit, onCreate: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text(if (english) "Create PDF from Photo" else "Criar PDF a partir de fotografia", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(9.dp))
                Column {
                    Text(if (english) "Ready to scan" else "Pronto para digitalizar", fontWeight = FontWeight.SemiBold)
                    Text(if (count == 0) (if (english) "Choose photos or use the camera" else "Escolhe fotografias ou usa a câmara") else if (english) "$count image(s) selected" else "$count imagem(ns) selecionada(s)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(22.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(62.dp))
                Spacer(Modifier.height(10.dp))
                Text(if (count == 0) (if (english) "Document preview" else "Pré-visualização do documento") else if (english) "Ready to create $count page(s)" else "Pronto para criar $count página(s)", fontWeight = FontWeight.SemiBold)
                Text(if (english) "Images are processed on this device" else "As imagens são processadas neste dispositivo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionSquare(Icons.Default.PhotoLibrary, if (english) "Gallery" else "Galeria", Modifier.weight(1f), onGallery)
            ActionSquare(Icons.Default.CameraAlt, if (english) "Camera" else "Câmara", Modifier.weight(1f), onCamera)
            ActionSquare(Icons.Default.DocumentScanner, if (english) "Scan" else "Digitalizar", Modifier.weight(1f), onCamera)
            ActionSquare(Icons.Default.Tune, if (english) "Filters" else "Filtros", Modifier.weight(1f), {})
        }
        Spacer(Modifier.height(22.dp))
        Button(onClick = onCreate, enabled = count > 0, modifier = Modifier.fillMaxWidth().height(60.dp), shape = RoundedCornerShape(17.dp)) {
            Text(if (english) "Create PDF" else "Criar PDF", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        if (count > 0) TextButton(onClick = onClear, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (english) "Clear selection" else "Limpar seleção") }
    }
}

@Composable
private fun ActionSquare(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier.height(86.dp), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Text(title, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun SelectPdfScreen(english: Boolean, title: String, subtitle: String, files: List<PdfDocumentItem>, onSelect: (File) -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 17.dp)) {
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        if (files.isEmpty()) EmptyState(if (english) "No PDFs in your library" else "Não existem PDFs na biblioteca", if (english) "Import a document to get started." else "Importa um documento para começar.", if (english) "Import PDF" else "Importar PDF", onImport)
        else LazyColumn {
            items(files, key = { it.file.absolutePath }) { item ->
                Card(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(item.formattedSize, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        Button(onClick = { onSelect(item.file) }, contentPadding = PaddingValues(horizontal = 12.dp)) { Text(if (english) "Select" else "Escolher") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MergeScreen(english: Boolean, files: List<PdfDocumentItem>, selected: List<String>, onToggle: (String) -> Unit, onMove: (String, Int) -> Unit, onImport: () -> Unit, onMerge: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(15.dp))
        Text(if (english) "Merge PDF" else "Juntar PDFs", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(if (english) "${selected.size} files selected · select at least two" else "${selected.size} ficheiro(s) · seleciona pelo menos dois", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        Text(if (english) "Use the arrows to change order." else "Usa as setas para alterar a ordem.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(files, key = { it.file.absolutePath }) { item ->
                val checked = selected.contains(item.file.absolutePath)
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, { onToggle(item.file.absolutePath) })
                        Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(item.formattedSize, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (checked) {
                            IconButton(onClick = { onMove(item.file.absolutePath, -1) }) { Icon(Icons.Default.KeyboardArrowUp, null) }
                            IconButton(onClick = { onMove(item.file.absolutePath, 1) }) { Icon(Icons.Default.KeyboardArrowDown, null) }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(if (english) "Add more files" else "Adicionar mais ficheiros") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onMerge, enabled = selected.size >= 2, modifier = Modifier.fillMaxWidth().height(57.dp), shape = RoundedCornerShape(16.dp)) { Text(if (english) "Merge now" else "Juntar agora", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        Text(if (english) "Files are processed locally on this device." else "Os ficheiros são processados localmente neste dispositivo.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(10.dp))
    }
}

@Composable
private fun ReaderScreen(english: Boolean, file: File, favorite: Boolean, onBack: () -> Unit, onShare: () -> Unit, onFavorite: () -> Unit, onAction: (String) -> Unit) {
    val image by produceState<ImageBitmap?>(null, file.absolutePath) { value = withContext(Dispatchers.IO) { renderPage(file, 0, 1000) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
            Text(file.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = onFavorite) { Icon(if (favorite) Icons.Default.Star else Icons.Default.StarBorder, null) }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, null) }
            IconButton(onClick = { onAction(if (english) "Search" else "Pesquisar") }) { Icon(Icons.Default.Search, null) }
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(10.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9EDF4)), contentAlignment = Alignment.Center) {
            if (image == null) CircularProgressIndicator() else Image(image!!, file.name, Modifier.fillMaxSize().padding(8.dp), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ReaderAction(Icons.Default.Highlight, if (english) "Highlight" else "Destacar") { onAction(if (english) "Highlight" else "Destacar") }
            ReaderAction(Icons.Default.Draw, if (english) "Draw" else "Desenhar") { onAction(if (english) "Draw" else "Desenhar") }
            ReaderAction(Icons.Default.TextFields, if (english) "Text" else "Texto") { onAction(if (english) "Text" else "Texto") }
            ReaderAction(Icons.Default.Edit, if (english) "Signature" else "Assinatura") { onAction(if (english) "Signature" else "Assinatura") }
        }
        Text(if (english) "Document preview · editing tools are being connected" else "Pré-visualização · as ferramentas de edição estão a ser ligadas", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp))
    }
}

@Composable
private fun ReaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label, fontSize = 10.sp) } }
}

@Composable
private fun ProcessingScreen(english: Boolean, title: String, detail: String) {
    Box(Modifier.fillMaxSize().padding(22.dp), contentAlignment = Alignment.Center) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.fillMaxWidth().padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(60.dp))
                Spacer(Modifier.height(18.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (english) "Processing locally. Please keep PDF Max open." else "A processar localmente. Mantém o PDF Max aberto.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun ResultScreen(english: Boolean, result: ResultFile, onShare: () -> Unit, onOpen: () -> Unit, onDone: () -> Unit) {
    val after = result.file.length()
    val saved = (result.beforeBytes ?: after) - after
    val percent = if (result.beforeBytes != null && result.beforeBytes > 0) ((saved.toDouble() / result.beforeBytes) * 100).roundToInt() else 0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(36.dp))
        Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(70.dp))
        Spacer(Modifier.height(16.dp))
        Text(result.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(result.file.name, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        if (result.beforeBytes != null) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column { Text(if (english) "Before" else "Antes", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatBytes(result.beforeBytes), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    Icon(Icons.Default.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                    Column(horizontalAlignment = Alignment.End) { Text(if (english) "After" else "Depois", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(formatBytes(after), fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                when {
                    percent > 0 -> if (english) "Saved $percent%" else "Redução real: $percent%"
                    saved == 0L -> if (english) "No size change" else "O tamanho não mudou"
                    else -> if (english) "Output is larger than the original" else "O ficheiro final ficou maior que o original"
                },
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold
            )
        } else Text(formatBytes(after), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(26.dp))
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text(if (english) "Share PDF" else "Partilhar PDF", fontSize = 17.sp) }
        Spacer(Modifier.height(9.dp))
        OutlinedButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (english) "Open document" else "Abrir documento") }
        TextButton(onClick = onDone) { Text(if (english) "Done" else "Concluído") }
    }
}

@Composable
private fun SettingsScreen(english: Boolean, dark: Boolean, storage: Long, onLanguage: () -> Unit, onTheme: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Header(if (english) "Settings" else "Configurações", english, {})
        Row(verticalAlignment = Alignment.CenterVertically) { Logo(58); Spacer(Modifier.width(12.dp)); Column { Text("PDF Max", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge); Text(if (english) "PDF Editor & Reader" else "Editor e leitor de PDF", color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        SettingSection(if (english) "GENERAL" else "GERAL")
        SettingRow(Icons.Default.Language, if (english) "Language" else "Idioma", if (english) "English" else "Português", onLanguage)
        SettingRow(Icons.Default.DarkMode, if (english) "Theme" else "Tema", if (dark) (if (english) "Dark" else "Escuro") else (if (english) "Light" else "Claro"), onTheme)
        SettingSection(if (english) "STORAGE & QUALITY" else "ARMAZENAMENTO E QUALIDADE")
        SettingRow(Icons.Default.Storage, if (english) "PDF Max storage" else "Armazenamento PDF Max", formatBytes(storage), {})
        SettingRow(Icons.Default.Tune, if (english) "Output quality" else "Qualidade de saída", if (english) "Best effort" else "Melhor esforço", {})
        SettingSection(if (english) "SUPPORT" else "SUPORTE")
        SettingRow(Icons.Default.Info, if (english) "About" else "Sobre", "PDF Max · v2.0.0", {})
        SettingRow(Icons.Default.HelpOutline, if (english) "Help" else "Ajuda", if (english) "Local PDF workspace" else "Ferramentas PDF locais", {})
        Spacer(Modifier.height(18.dp))
        Text(if (english) "Your files stay on this device unless you choose to share them." else "Os teus ficheiros permanecem neste dispositivo, exceto quando escolhes partilhá-los.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
}

@Composable
private fun SettingSection(title: String) {
    Text(title, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
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
private fun QuickLink(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
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
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
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
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onAction) { Text(action) }
    }
}

private fun formatBytes(value: Long): String = when {
    value >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f MB", value / (1024f * 1024f))
    value >= 1024L -> String.format(Locale.getDefault(), "%.0f KB", value / 1024f)
    else -> "$value B"
}
