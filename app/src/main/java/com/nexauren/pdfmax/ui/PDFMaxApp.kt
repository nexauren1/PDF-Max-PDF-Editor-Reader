package com.nexauren.pdfmax.ui

import android.net.Uri
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.io.File
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.nexauren.pdfmax.R
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexauren.pdfmax.ui.theme.*
import com.nexauren.pdfmax.data.DocumentKind
import com.nexauren.pdfmax.data.DocumentScanner
import com.nexauren.pdfmax.data.DocumentActions
import com.nexauren.pdfmax.data.PdfOperations
import com.nexauren.pdfmax.data.IndexedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

internal enum class Page { HOME, FILES, FAVORITES, RECENT, TRASH, TOOLS, READER, OFFICE, CREATE_PDF, EDITOR, CONVERTER, SCANNER, PROTECT, SETTINGS, TOOL }
internal data class ToolSpec(val title: String, val subtitle: String, val icon: ImageVector, val color: Color, val page: Page = Page.TOOL)

private val tools = listOf(
    ToolSpec("Criar PDF", "Laboratório de escrita e layout", Icons.Default.Description, Color(0xFFE65A82), Page.CREATE_PDF),
    ToolSpec("Editar PDF", "Sobreposição de texto em páginas PDF", Icons.Default.Edit, Color(0xFFFF3D79), Page.EDITOR),
    ToolSpec("Converter PDF", "Word, Excel e imagem", Icons.Default.SwapHoriz, Color(0xFF2675FF), Page.CONVERTER),
    ToolSpec("Comprimir PDF", "Regravar e otimizar; tamanho pode variar", Icons.Default.Compress, Color(0xFF18A96B)),
    ToolSpec("Juntar PDF", "Combinar documentos", Icons.Default.MergeType, Color(0xFF9B4DFF)),
    ToolSpec("Dividir PDF", "Separar por páginas", Icons.Default.CallSplit, Color(0xFFFF922E)),
    ToolSpec("Proteger PDF", "Encriptação AES e palavra-passe de abertura", Icons.Default.Security, Color(0xFF11AFC0), Page.PROTECT),
    ToolSpec("Assinar PDF", "Assinatura manuscrita visual", Icons.Default.Draw, Color(0xFF9A59FF)),
    ToolSpec("Digitalizar e OCR", "Documento para PDF", Icons.Default.QrCodeScanner, Color(0xFF2ABF78), Page.SCANNER),
    ToolSpec("Extrair imagens", "Guardar imagens", Icons.Default.Image, Color(0xFF18A5E8)),
    ToolSpec("Adicionar texto", "Inserir texto", Icons.Default.TextFields, Color(0xFF17B7D3), Page.EDITOR),
    ToolSpec("Marca de água", "Texto ou imagem", Icons.Default.WaterDrop, Color(0xFF527DFF)),
    ToolSpec("Numerar páginas", "Cabeçalho e rodapé", Icons.Default.FormatListNumbered, Color(0xFF00B99A)),
    ToolSpec("Rodar páginas", "Orientação do PDF", Icons.Default.RotateRight, Color(0xFF19B5A2)),
    ToolSpec("Comparar PDFs", "Ver diferenças", Icons.Default.CompareArrows, Color(0xFFAA63F8)),
    ToolSpec("Traduzir PDF", "Vários idiomas", Icons.Default.Translate, Color(0xFF3989F4)),
    ToolSpec("Preencher formulários", "Campos e formulários", Icons.Default.EditNote, Color(0xFF4A8DFF)),
    ToolSpec("Reparar PDF", "Tentar recuperar PDFs legíveis", Icons.Default.Refresh, Color(0xFF15B9A3)),
    ToolSpec("PDF para imagem", "JPG e PNG", Icons.Default.Photo, Color(0xFFB05BF6)),
    ToolSpec("Imagem para PDF", "JPG, PNG e mais", Icons.Default.Collections, Color(0xFF12A9D8)),
    ToolSpec("Organizar páginas", "Reordenar e eliminar", Icons.Default.GridView, Color(0xFF168DFF)),
    ToolSpec("Imprimir PDF", "Preparar impressão", Icons.Default.Print, Color(0xFF7C70F5)),
    ToolSpec("Partilhar PDF", "Enviar documento", Icons.Default.Share, Color(0xFF16B89B)),
    ToolSpec("Ver propriedades", "Informações do ficheiro", Icons.Default.Info, Color(0xFF4F8FEA)),
    ToolSpec("Remover páginas", "Eliminar páginas selecionadas", Icons.Default.Delete, Color(0xFFEB5A74)),
    ToolSpec("Extrair texto PDF", "Guardar texto como TXT", Icons.Default.TextSnippet, Color(0xFF1CBCD0)),
    ToolSpec("Criar ZIP", "Arquivar vários ficheiros", Icons.Default.FolderZip, Color(0xFFE2A33D)),
    ToolSpec("Detetar duplicados", "Encontrar cópias exatas", Icons.Default.ContentCopy, Color(0xFF9B6BFF)),
    ToolSpec("Extrair páginas", "Criar um PDF com páginas escolhidas", Icons.Default.Description, Color(0xFF398BFF)),
    ToolSpec("Adicionar página em branco", "Inserir uma nova página", Icons.Default.NoteAdd, Color(0xFF16B89B)),
    ToolSpec("Editar metadados", "Título, autor e palavras-chave", Icons.Default.EditNote, Color(0xFFFF8A45)),
    ToolSpec("Achatar formulários", "Transformar campos AcroForm em conteúdo fixo", Icons.Default.Layers, Color(0xFF17AFA0)),
    ToolSpec("Remover metadados", "Limpar autor, título, datas e XMP do documento", Icons.Default.PrivacyTip, Color(0xFFE66A83)),
    ToolSpec("Relatório do PDF", "Contagem de páginas, palavras e texto extraível", Icons.Default.Analytics, Color(0xFF5A91F3))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDFMaxApp(initialPdf: Uri? = null) {
    var screen by remember { mutableStateOf(if (initialPdf == null) Page.HOME else Page.READER) }
    var previousScreen by remember { mutableStateOf(Page.HOME) }
    var currentTool by remember { mutableStateOf(tools[0]) }
    var pdfUri by remember { mutableStateOf(initialPdf) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var pdfName by remember { mutableStateOf("Relatório_Final.pdf") }
    val appContext = LocalContext.current
    var english by remember {
        mutableStateOf(
            appContext.getSharedPreferences("pdfmax_preferences", android.content.Context.MODE_PRIVATE)
                .getBoolean("english", false)
        )
    }
    fun toggleLanguage() {
        english = !english
        appContext.getSharedPreferences("pdfmax_preferences", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("english", english).apply()
    }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun go(target: Page) {
        if (target != screen) previousScreen = screen
        screen = target
        scope.launch { drawer.close() }
    }
    fun openTool(tool: ToolSpec) {
        previousScreen = screen
        currentTool = tool
        screen = tool.page
    }
    fun backFromWorkspace() {
        val destination = previousScreen
        screen = destination
        previousScreen = Page.HOME
        scope.launch { drawer.close() }
    }
    fun openLocalFile(file: File) {
        val libraryPrefs = appContext.getSharedPreferences("pdfmax_library", android.content.Context.MODE_PRIVATE)
        val path = file.absolutePath
        val recent = libraryPrefs.getString("recent_paths", "").orEmpty()
            .split("\n").filter { it.isNotBlank() && it != path }
        libraryPrefs.edit().putString("recent_paths", (listOf(path) + recent).take(50).joinToString("\n")).apply()
        selectedFile = file
        pdfName = file.name
        pdfUri = Uri.fromFile(file)
        screen = if (file.extension.equals("pdf", true)) Page.READER else Page.OFFICE
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = Color(0xB8000616),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(302.dp),
                drawerContainerColor = Color(0xFF09172D),
                drawerContentColor = MaxText
            ) {
                SideMenu(screen, english, { go(it) }, { toggleLanguage() })
            }
        }
    ) {
        Scaffold(
            containerColor = MaxNavy,
            bottomBar = {
                if (screen !in setOf(Page.READER, Page.OFFICE, Page.EDITOR, Page.CONVERTER, Page.SCANNER, Page.PROTECT, Page.TOOL)) {
                    BottomBar(
                        Modifier.fillMaxWidth(), screen, english,
                        { go(Page.HOME) }, { go(Page.FILES) },
                        { go(Page.CREATE_PDF) },
                        { go(Page.FAVORITES) }, { scope.launch { drawer.open() } }
                    )
                }
            }
        ) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets)
                    .background(Brush.verticalGradient(listOf(Color(0xFF071329), Color(0xFF08162B), Color(0xFF050E20))))
            ) {
                AnimatedContent(
                    targetState = screen to (if (screen == Page.TOOL) currentTool else tools.first()),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(220)) +
                            slideInHorizontally(animationSpec = tween(220)) { width -> width / 18 }) togetherWith
                            (fadeOut(animationSpec = tween(160)) +
                                slideOutHorizontally(animationSpec = tween(160)) { width -> -width / 24 })
                    },
                    label = "PDF Max screen transition"
                ) { destination ->
                    val (targetPage, targetTool) = destination
                    when (targetPage) {
                        Page.HOME -> Dashboard(english, { go(it) }, { openTool(it) }, { go(Page.FILES) }, ::openLocalFile)
                        Page.FILES -> DocumentLibraryScreen(english, ::openLocalFile)
                        Page.FAVORITES -> DocumentLibraryScreen(english, ::openLocalFile, initialFilter = "Favorites")
                        Page.RECENT -> DocumentLibraryScreen(english, ::openLocalFile, initialFilter = "Recent")
                        Page.TRASH -> DocumentLibraryScreen(english, ::openLocalFile, initialFilter = "Trash")
                        Page.OFFICE -> OfficePreviewScreen(selectedFile, english) { go(Page.FILES) }
                        Page.TOOLS -> ToolsPage(english, { openTool(it) })
                        Page.READER -> ReaderPage(english, pdfName, pdfUri, selectedFile, { go(Page.FILES) }, { go(Page.EDITOR) }, { openTool(it) })
                        Page.CREATE_PDF -> PdfCreatorScreen(english)
                        Page.EDITOR -> ToolWorkspaceScreen(tools.first { it.title == "Editar PDF" }, english, ::backFromWorkspace)
                        Page.CONVERTER -> ToolWorkspaceScreen(tools.first { it.title == "Converter PDF" }, english, ::backFromWorkspace)
                        Page.SCANNER -> ToolWorkspaceScreen(tools.first { it.title == "Digitalizar e OCR" }, english, ::backFromWorkspace)
                        Page.PROTECT -> ToolWorkspaceScreen(tools.first { it.title == "Proteger PDF" }, english, ::backFromWorkspace)
                        Page.SETTINGS -> SettingsPage(english, { toggleLanguage() })
                        Page.TOOL -> ToolWorkspaceScreen(targetTool, english, ::backFromWorkspace)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBar(screen: Page, english: Boolean, onMenu: () -> Unit, onBack: () -> Unit, onSettings: () -> Unit) {
    val root = screen == Page.HOME || screen == Page.FILES || screen == Page.TOOLS
    Row(Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = if (root) onMenu else onBack) {
            Icon(if (root) Icons.Default.Menu else Icons.Default.ArrowBack, null, tint = Color.White)
        }
        Image(
            painter = painterResource(R.drawable.ic_pdfmax),
            contentDescription = "PDF Max",
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp))
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("PDF Max", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (english) "PDF Editor & Reader" else "Editor e Leitor de PDF", fontSize = 10.sp, color = MaxMuted)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = Color(0xFFC1D2EE)) }
    }
}

@Composable
private fun Dashboard(en: Boolean, navigate: (Page) -> Unit, choose: (ToolSpec) -> Unit, openPdf: () -> Unit, openFile: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var storageAccess by remember { mutableStateOf(DocumentScanner.hasAccess(context)) }
    var recentDocuments by remember { mutableStateOf(DocumentScanner.cachedDocuments().take(5)) }

    fun openStorageSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        }
        runCatching { context.startActivity(intent) }.onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val newlyGranted = !storageAccess && DocumentScanner.hasAccess(context)
                val granted = DocumentScanner.hasAccess(context)
                storageAccess = granted
                if (granted) {
                    scope.launch {
                        recentDocuments = withContext(Dispatchers.IO) {
                            // A full reconciliation is needed only on the first grant.
                            // Normal resumes reuse the cached index and incremental refresh.
                            DocumentScanner.scan(context, forceRefresh = newlyGranted)
                        }.take(5)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        // Load the saved library first, then refresh MediaStore in the background.
        val saved = withContext(Dispatchers.IO) { DocumentScanner.loadPersistent(context) }
        recentDocuments = saved.take(5)
        val latest = withContext(Dispatchers.IO) { DocumentScanner.scan(context) }
        recentDocuments = latest.take(5)
        while (true) {
            delay(DocumentScanner.REFRESH_INTERVAL_MS)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                val updated = withContext(Dispatchers.IO) { DocumentScanner.scan(context) }
                recentDocuments = updated.take(5)
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 7.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_pdfmax),
                contentDescription = "PDF Max",
                modifier = Modifier.size(45.dp).clip(RoundedCornerShape(13.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("PDF Max", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (en) "PDF EDITOR & READER" else "EDITOR E LEITOR DE PDF",
                    color = Color(0xFF8FA9CE), fontSize = 8.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp
                )
            }
            Row(
                Modifier.clip(CircleShape)
                    .background(Color(0xFF352A0B))
                    .border(1.dp, Color(0xFFFFD45B).copy(alpha = 0.65f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, null, tint = Color(0xFFFFD45B), modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("PRO", color = Color(0xFFFFD45B), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
            IconButton(onClick = { navigate(Page.SETTINGS) }) {
                Icon(Icons.Default.Settings, if (en) "Settings" else "Definições", tint = Color(0xFFB5C7E2))
            }
        }

        SearchBar(if (en) "Search files and folders…" else "Pesquisar ficheiros e pastas…") {
            navigate(Page.FILES)
        }

        if (!storageAccess) {
            Spacer(Modifier.height(11.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF17304D))
                    .border(1.dp, Color(0xFF3B638B), RoundedCornerShape(14.dp))
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FolderSpecial, null, tint = Color(0xFF8BD6FF), modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (en) "Enable document access" else "Ative o acesso aos documentos", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (en) "Needed to find PDFs, Word and other files on this device." else "Necessário para localizar PDFs, Word e outros ficheiros neste dispositivo.",
                        color = MaxMuted, fontSize = 9.sp, lineHeight = 13.sp
                    )
                }
                TextButton(onClick = { openStorageSettings() }) {
                    Text(if (en) "Allow" else "Ativar", color = Color(0xFF8BD6FF), fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(13.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF32117B), Color(0xFF123D9A), Color(0xFF075C82))))
                .border(1.dp, Color(0xFF347DFF), RoundedCornerShape(19.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (en) "YOUR COMPLETE PDF ASSISTANT" else "O SEU ASSISTENTE COMPLETO DE PDF",
                        color = Color(0xFFC7BCFF), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (en) "All your PDF tools in one place" else "Todas as ferramentas num só lugar",
                        color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                        lineHeight = 21.sp
                    )
                    Spacer(Modifier.height(5.dp))
                    Text(
                        if (en) "Edit, convert, protect and organise documents with one workspace." else "Edite, converta, proteja e organize os seus documentos num único espaço.",
                        color = Color(0xFFD8E5FF), fontSize = 10.sp, lineHeight = 14.sp
                    )
                    Spacer(Modifier.height(11.dp))
                    Row(
                        Modifier.clip(CircleShape)
                            .background(Color(0xFF087FF1))
                            .clickable { navigate(Page.TOOLS) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (en) "Explore all tools" else "Explorar ferramentas", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier.size(76.dp).clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFF9C56FF), Color(0xFF126CFF), Color(0xFF0BD4FF)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(49.dp))
                }
            }
        }

        Spacer(Modifier.height(17.dp))
        SectionTitle(if (en) "Main tools" else "Ferramentas principais", if (en) "See all" else "Ver todas") {
            navigate(Page.TOOLS)
        }
        ToolGrid(
            listOf(tools[1], tools[2], tools[3], tools[4], tools[5], tools[6]),
            choose,
            en
        )
        Spacer(Modifier.height(17.dp))
        SectionTitle(if (en) "Recent files" else "Arquivos recentes", if (en) "View all" else "Ver todos") {
            navigate(Page.FILES)
        }
        if (recentDocuments.isEmpty()) {
            Text(if (en) "Your recent documents will appear here after enabling the file library." else "Os documentos recentes aparecerão aqui depois de ativar a biblioteca de ficheiros.", color = MaxMuted, fontSize = 10.sp, lineHeight = 15.sp)
        } else {
            recentDocuments.forEach { item ->
                val accent = when (item.kind) {
                    DocumentKind.PDF -> Color(0xFFFF477A)
                    DocumentKind.WORD -> Color(0xFF398BFF)
                    DocumentKind.EXCEL -> Color(0xFF1CC99A)
                    DocumentKind.POWERPOINT -> Color(0xFFFF9B3D)
                    DocumentKind.IMAGE -> Color(0xFF9D62F5)
                    else -> Color(0xFF24B9C7)
                }
                RecentRow(item.name, item.sizeLabel + "  •  " + item.extension.uppercase(), accent) { openFile(item.file) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FolderOpen, null, tint = MaxCyan)
            Spacer(Modifier.width(8.dp))
            Text(if (en) "Open a PDF from your device" else "Abrir um PDF do dispositivo", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = openPdf) { Text(if (en) "Open" else "Abrir", color = MaxCyan) }
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onAction, contentPadding = PaddingValues(4.dp)) {
            Text(action, color = MaxCyan, fontSize = 11.sp)
            Icon(Icons.Default.ChevronRight, null, tint = MaxCyan, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun ToolGrid(list: List<ToolSpec>, onChoose: (ToolSpec) -> Unit, english: Boolean = false) {
    // Three vivid columns, matching the reference screens while keeping touch
    // targets large enough for compact Android phones.
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        list.chunked(3).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                row.forEach { tool ->
                    ToolTile(tool, Modifier.weight(1f), english) { onChoose(tool) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToolTile(
    tool: ToolSpec,
    modifier: Modifier = Modifier,
    english: Boolean = false,
    click: () -> Unit
) {
    Column(
        modifier.fillMaxWidth().heightIn(min = 112.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        tool.color.copy(alpha = 0.92f),
                        tool.color.copy(alpha = 0.58f),
                        Color(0xFF111A36)
                    )
                )
            )
            .border(1.dp, tool.color.copy(alpha = 0.82f), RoundedCornerShape(17.dp))
            .clickable(onClick = click)
            .padding(horizontal = 5.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size(43.dp).clip(RoundedCornerShape(13.dp))
                .background(Color.White.copy(alpha = 0.17f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            localizedToolTitle(tool.title, english),
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            lineHeight = 11.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentRow(name: String, detail: String, color: Color, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = click).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(39.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = 0.20f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PictureAsPdf, null, tint = color, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaxMuted, fontSize = 10.sp)
        }
        Icon(Icons.Default.MoreVert, null, tint = MaxMuted)
    }
    Divider(color = Color(0xFF1B2D49))
}

@Composable
private fun FilesPage(en: Boolean, fileName: String, openPdf: () -> Unit, openReader: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(if (en) "My files" else "Meus Arquivos", if (en) "Your documents, organized in one place" else "Os seus documentos organizados num só lugar")
        SearchBar(if (en) "Search files and folders…" else "Pesquisar arquivos e pastas…") {}
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf(if (en) "All" else "Todos", "PDF", if (en) "Documents" else "Documentos", if (en) "Images" else "Imagens").forEachIndexed { i, label ->
                Box(Modifier.clip(CircleShape).background(if (i == 0) Color(0xFF087EEB) else Color(0xFF172A47)).padding(horizontal = 13.dp, vertical = 8.dp)) {
                    Text(label, color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(if (en) "FOLDERS" else "PASTAS", color = MaxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        listOf(
            Triple(if (en) "Documents" else "Documentos", "12 itens", Color(0xFF3F9DFF)),
            Triple(if (en) "Projects" else "Projetos", "8 itens", Color(0xFFFFB23F)),
            Triple(if (en) "Images" else "Imagens", "24 itens", Color(0xFF18BFA7)),
            Triple(if (en) "Downloads" else "Transferências", "6 itens", Color(0xFF9B68F4))
        ).forEach { folder ->
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = folder.third, modifier = Modifier.size(31.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(folder.first, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(folder.second, color = MaxMuted, fontSize = 10.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = MaxMuted)
            }
            Divider(color = Color(0xFF1A2B47))
        }
        Spacer(Modifier.height(17.dp))
        SectionTitle(if (en) "Recent documents" else "Documentos recentes", if (en) "Import" else "Importar", openPdf)
        RecentRow(fileName, "2.4 MB  •  PDF", Color(0xFFFF477A), openReader)
        RecentRow("Apresentação.pdf", "5.8 MB  •  PDF", Color(0xFF9D62F5), openReader)
        RecentRow("Contrato.pdf", "1.2 MB  •  PDF", Color(0xFF1CC99A), openReader)
        Spacer(Modifier.height(16.dp))
        PrimaryAction(if (en) "Import PDF" else "Importar PDF", Icons.Default.UploadFile, openPdf)
    }
}

private fun toolCategory(title: String): String = when (title) {
    "Editar PDF", "Adicionar texto", "Marca de água", "Assinar PDF", "Preencher formulários", "Editar metadados" -> "Edit"
    "Achatar formulários", "Remover metadados" -> "Security"
    "Relatório do PDF" -> "Utilities"
    "Juntar PDF", "Dividir PDF", "Organizar páginas", "Remover páginas", "Extrair páginas",
    "Adicionar página em branco", "Rodar páginas" -> "Organize"
    "Converter PDF", "PDF para imagem", "Imagem para PDF", "Extrair imagens", "Extrair texto PDF", "Criar PDF" -> "Convert"
    "Proteger PDF" -> "Security"
    "Digitalizar e OCR" -> "Scan"
    else -> "Utilities"
}

@Composable
private fun ToolsPage(en: Boolean, choose: (ToolSpec) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Edit", "Organize", "Convert", "Security", "Scan", "Utilities")
    val categoryLabels = categories.associateWith { key ->
        when (key) {
            "All" -> if (en) "All tools" else "Todas"
            "Edit" -> if (en) "Edit" else "Editar"
            "Organize" -> if (en) "Organize" else "Organizar"
            "Convert" -> if (en) "Convert" else "Converter"
            "Security" -> if (en) "Security" else "Segurança"
            "Scan" -> if (en) "Scan & OCR" else "Digitalizar e OCR"
            else -> if (en) "Utilities" else "Utilitários"
        }
    }
    val categoryTools = tools.filter { selectedCategory == "All" || toolCategory(it.title) == selectedCategory }
    val visibleTools = categoryTools.filter {
        query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true) ||
            localizedToolTitle(it.title, en).contains(query, true) ||
            localizedToolSubtitle(it.title, en).contains(query, true)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(
            if (en) "All tools" else "Todas as ferramentas",
            if (en) "A complete workspace for PDF documents" else "Um espaço completo para trabalhar com documentos PDF"
        )
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF142F57), Color(0xFF10213B))))
                .border(1.dp, Color(0xFF294C7B), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(43.dp).clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF168DEB), Color(0xFF7555F6)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (en) "Your document toolkit" else "O seu conjunto de ferramentas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    if (en) "${tools.size} tools · Pick a category to get started" else "${tools.size} ferramentas · Escolha uma categoria para começar",
                    color = MaxMuted, fontSize = 10.sp
                )
            }
            Icon(Icons.Default.GridView, null, tint = MaxCyan, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text(if (en) "Search a tool…" else "Pesquisar uma ferramenta…", color = MaxMuted, fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = MaxMuted) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Default.Close, null, tint = MaxMuted)
                    }
                }
            },
            colors = darkFieldColors()
        )
        Spacer(Modifier.height(13.dp))
        Text(
            if (en) "BROWSE BY CATEGORY" else "EXPLORAR POR CATEGORIA",
            color = MaxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                val count = if (category == "All") tools.size else tools.count { toolCategory(it.title) == category }
                FilterChip(
                    selected = selectedCategory == category,
                    onClick = { selectedCategory = category },
                    label = {
                        Text("${categoryLabels[category]}  $count", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF164A75),
                        selectedLabelColor = Color.White,
                        containerColor = Color(0xFF10213A),
                        labelColor = MaxMuted
                    )
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        SectionTitle(
            if (en) categoryLabels[selectedCategory].orEmpty() else "Ferramentas",
            visibleTools.size.toString()
        ) {}
        if (visibleTools.isEmpty()) {
            InfoPanel(
                if (en) "No tools found" else "Nenhuma ferramenta encontrada",
                if (en) "Try a different word or search by what you want to do." else "Experimente outra palavra ou pesquise pela tarefa que pretende realizar.",
                Icons.Default.Search
            )
        } else {
            ToolGrid(visibleTools, choose, en)
        }
        Spacer(Modifier.height(18.dp))
        InfoPanel(
            if (en) "A dedicated workspace for every tool" else "Um espaço próprio para cada ferramenta",
            if (en) "Select your files, watch the processing and review the result before saving." else "Selecione os ficheiros, acompanhe o processamento e reveja o resultado antes de guardar.",
            Icons.Default.AutoAwesome
        )
    }
}

@Composable
private fun ReaderPage(
    en: Boolean,
    fileName: String,
    uri: Uri?,
    file: File?,
    openPdf: () -> Unit,
    edit: () -> Unit,
    choose: (ToolSpec) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pageNumber by remember(uri) { mutableIntStateOf(1) }
    var pageCount by remember(uri) { mutableIntStateOf(1) }
    var zoom by remember(uri) { mutableFloatStateOf(1f) }
    var nightMode by remember(uri) { mutableStateOf(false) }
    var showSearch by remember(uri) { mutableStateOf(false) }
    var searchQuery by remember(uri) { mutableStateOf("") }
    var searchReport by remember(uri) { mutableStateOf("") }
    var searching by remember(uri) { mutableStateOf(false) }
    var showProperties by remember(uri) { mutableStateOf(false) }
    var propertiesText by remember(uri) { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }

    val bookmarkKey = "bookmarks:" + (file?.absolutePath ?: uri?.toString() ?: fileName)
    val readerPrefs = remember(context) { context.getSharedPreferences("pdfmax_reader", android.content.Context.MODE_PRIVATE) }
    var bookmarks by remember(bookmarkKey) {
        mutableStateOf(readerPrefs.getStringSet(bookmarkKey, emptySet()).orEmpty().toSet())
    }
    val currentPageKey = pageNumber.toString()
    val isBookmarked = currentPageKey in bookmarks

    fun toggleBookmark() {
        val next = if (isBookmarked) bookmarks - currentPageKey else bookmarks + currentPageKey
        bookmarks = next
        readerPrefs.edit().putStringSet(bookmarkKey, next.toMutableSet()).apply()
    }

    fun showActionError(message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }

    fun shareCurrent() {
        val source = file ?: uri?.takeIf { it.scheme == "file" }?.path?.let(::File)
        if (source == null || !source.isFile) {
            showActionError(if (en) "This document cannot be shared from this reader session." else "Não foi possível partilhar este documento nesta sessão.")
            return
        }
        runCatching { DocumentActions.share(context, source) }
            .onFailure { showActionError(it.message ?: "Falha ao partilhar.") }
    }

    fun printCurrent() {
        val source = file ?: uri?.takeIf { it.scheme == "file" }?.path?.let(::File)
        if (source == null || !source.isFile) {
            showActionError(if (en) "This document cannot be printed from this reader session." else "Não foi possível imprimir este documento nesta sessão.")
            return
        }
        runCatching { DocumentActions.print(context, source) }
            .onFailure { showActionError(it.message ?: "Falha ao imprimir.") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = openPdf) {
                Icon(Icons.Default.ArrowBack, if (en) "Back to files" else "Voltar aos ficheiros", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(fileName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (en) "$pageNumber of $pageCount pages" else "$pageNumber de $pageCount páginas",
                    color = MaxMuted, fontSize = 10.sp
                )
            }
            IconButton(onClick = { toggleBookmark() }) {
                Icon(
                    if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    if (en) "Bookmark page" else "Marcar página",
                    tint = if (isBookmarked) MaxCyan else MaxMuted
                )
            }
            Box {
                IconButton(onClick = { showMore = true }) { Icon(Icons.Default.MoreVert, null, tint = MaxText) }
                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                    DropdownMenuItem(
                        text = { Text(if (en) "Open another PDF" else "Abrir outro PDF") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        onClick = { showMore = false; openPdf() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (en) "Share PDF" else "Partilhar PDF") },
                        leadingIcon = { Icon(Icons.Default.Share, null) },
                        onClick = { showMore = false; shareCurrent() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (en) "Print PDF" else "Imprimir PDF") },
                        leadingIcon = { Icon(Icons.Default.Print, null) },
                        onClick = { showMore = false; printCurrent() }
                    )
                    DropdownMenuItem(
                        text = { Text(if (en) "Document properties" else "Propriedades do documento") },
                        leadingIcon = { Icon(Icons.Default.Info, null) },
                        onClick = {
                            showMore = false
                            scope.launch {
                                val source = file ?: uri?.takeIf { it.scheme == "file" }?.path?.let(::File)
                                propertiesText = if (source != null && source.isFile) {
                                    runCatching { withContext(Dispatchers.IO) { PdfOperations.getProperties(context, source) } }
                                        .getOrElse { it.message ?: "Não foi possível ler as propriedades." }
                                } else if (en) "Properties are unavailable for this document source." else "As propriedades não estão disponíveis para esta origem."
                                showProperties = true
                            }
                        }
                    )
                }
            }
        }

        PdfPagePreview(
            uri = uri,
            modifier = Modifier.padding(top = 3.dp),
            pageIndex = pageNumber - 1,
            zoom = zoom,
            nightMode = nightMode,
            onPageCount = { count ->
                pageCount = count.coerceAtLeast(1)
                pageNumber = pageNumber.coerceIn(1, pageCount)
            }
        )
        Spacer(Modifier.height(9.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF112441))
                .border(1.dp, Color(0xFF294D78), RoundedCornerShape(13.dp)).padding(horizontal = 5.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            IconButton(onClick = { pageNumber = (pageNumber - 1).coerceAtLeast(1) }, enabled = pageNumber > 1) {
                Icon(Icons.Default.ChevronLeft, null, tint = if (pageNumber > 1) MaxCyan else MaxMuted)
            }
            Text(if (en) "Page $pageNumber / $pageCount" else "Página $pageNumber / $pageCount", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            IconButton(onClick = { pageNumber = (pageNumber + 1).coerceAtMost(pageCount) }, enabled = pageNumber < pageCount) {
                Icon(Icons.Default.ChevronRight, null, tint = if (pageNumber < pageCount) MaxCyan else MaxMuted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF112441))
                .border(1.dp, Color(0xFF294D78), RoundedCornerShape(15.dp)).padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ReaderAction(Icons.Default.ZoomIn, "${(zoom * 100).toInt()}%", en) {
                zoom = when {
                    zoom < 1.2f -> 1.25f
                    zoom < 1.45f -> 1.5f
                    zoom < 1.8f -> 2f
                    else -> 1f
                }
            }
            ReaderAction(Icons.Default.Search, if (en) "Search" else "Pesquisar", en) {
                searchQuery = ""
                searchReport = ""
                showSearch = true
            }
            ReaderAction(
                if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                if (isBookmarked) { if (en) "Saved" else "Marcado" } else { if (en) "Bookmark" else "Marcador" },
                en,
                ::toggleBookmark
            )
            ReaderAction(
                Icons.Default.DarkMode,
                if (nightMode) { if (en) "Day" else "Dia" } else { if (en) "Night" else "Noite" },
                en
            ) { nightMode = !nightMode }
            ReaderAction(Icons.Default.Share, if (en) "Share" else "Partilhar", en, ::shareCurrent)
            ReaderAction(Icons.Default.Print, if (en) "Print" else "Imprimir", en, ::printCurrent)
        }

        if (bookmarks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle(if (en) "Bookmarks" else "Marcadores", bookmarks.size.toString()) {}
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                bookmarks.mapNotNull { it.toIntOrNull() }.sorted().forEach { bookmarkedPage ->
                    Box(
                        Modifier.clip(CircleShape)
                            .background(if (bookmarkedPage == pageNumber) Color(0xFF164A75) else Color(0xFF172A47))
                            .clickable { pageNumber = bookmarkedPage.coerceIn(1, pageCount) }
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    ) {
                        Text(
                            (if (en) "Page " else "Página ") + bookmarkedPage,
                            color = Color.White, fontSize = 10.sp,
                            fontWeight = if (bookmarkedPage == pageNumber) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        SectionTitle(if (en) "Pages" else "Páginas", if (en) "Organize" else "Organizar") {
            choose(tools.first { it.title == "Organizar páginas" })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..pageCount.coerceAtMost(5)).forEach { n ->
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .border(if (pageNumber == n) 2.dp else 1.dp, if (pageNumber == n) MaxBlue else Color(0xFF263C5D), RoundedCornerShape(8.dp))
                        .background(Color(0xFF172943)).clickable { pageNumber = n }.padding(5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PdfPagePreview(
                        uri = uri,
                        modifier = Modifier.fillMaxWidth().height(76.dp),
                        pageIndex = n - 1,
                        thumbnail = true
                    )
                    Text("$n", color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryAction(if (en) "Edit PDF" else "Editar PDF", Icons.Default.Edit, edit, Modifier.weight(1f))
            SecondaryAction(if (en) "More tools" else "Mais ferramentas", Icons.Default.GridView, { choose(tools[20]) }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
    }

    if (showSearch) {
        AlertDialog(
            onDismissRequest = { showSearch = false },
            title = { Text(if (en) "Search document text" else "Pesquisar texto no documento", color = Color.White) },
            text = {
                Column {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        label = { Text(if (en) "Text to find" else "Texto a encontrar") },
                        colors = darkFieldColors()
                    )
                    if (searchReport.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(searchReport, color = MaxMuted, fontSize = 10.sp, lineHeight = 14.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !searching && searchQuery.isNotBlank(),
                    onClick = {
                        val source = file ?: uri?.takeIf { it.scheme == "file" }?.path?.let(::File)
                        if (source == null || !source.isFile) {
                            searchReport = if (en) "Text search is unavailable for this source." else "A pesquisa de texto não está disponível para esta origem."
                        } else {
                            scope.launch {
                                searching = true
                                searchReport = if (en) "Searching…" else "A pesquisar…"
                                searchReport = runCatching {
                                    val matches = withContext(Dispatchers.IO) {
                                        PdfOperations.extractText(context, source)
                                    }.lineSequence().filter { it.contains(searchQuery, ignoreCase = true) }.take(25).toList()
                                    if (matches.isEmpty()) {
                                        if (en) "No matching text was found." else "Não foi encontrado texto correspondente."
                                    } else {
                                        (if (en) "${matches.size} matching line(s):\n" else "${matches.size} linha(s) encontrada(s):\n") +
                                            matches.joinToString("\n") { "• " + it.take(180) }
                                    }
                                }.getOrElse { it.message ?: (if (en) "Search failed." else "A pesquisa falhou.") }
                                searching = false
                            }
                        }
                    }
                ) { Text(if (searching) { if (en) "Searching…" else "A pesquisar…" } else { if (en) "Search" else "Pesquisar" }, color = MaxCyan) }
            },
            dismissButton = {
                TextButton(onClick = { showSearch = false }) { Text(if (en) "Close" else "Fechar", color = MaxMuted) }
            },
            containerColor = Color(0xFF102541)
        )
    }

    if (showProperties) {
        AlertDialog(
            onDismissRequest = { showProperties = false },
            title = { Text(if (en) "Document properties" else "Propriedades do documento", color = Color.White) },
            text = { Text(propertiesText, color = MaxMuted, fontSize = 11.sp, lineHeight = 16.sp) },
            confirmButton = { TextButton(onClick = { showProperties = false }) { Text(if (en) "Close" else "Fechar", color = MaxCyan) } },
            containerColor = Color(0xFF102541)
        )
    }
}

@Composable
private fun ReaderAction(icon: ImageVector, label: String, english: Boolean, click: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 43.dp).clip(RoundedCornerShape(8.dp))
            .clickable(onClick = click).padding(horizontal = 3.dp, vertical = 4.dp)
    ) {
        Icon(icon, null, tint = Color(0xFFE4EEFF), modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFFD6E2F7), fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun EditorPage(en: Boolean, uri: Uri?, openPdf: () -> Unit, choose: (ToolSpec) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (en) "Edit PDF" else "Editar PDF", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        PdfPagePreview(uri)
        Spacer(Modifier.height(10.dp))
        Text(if (en) "EDITING TOOLS" else "FERRAMENTAS DE EDIÇÃO", color = MaxMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        ToolGrid(
            listOf("Adicionar texto", "Editar PDF", "Assinar PDF", "Marca de água", "Preencher formulários", "Rodar páginas")
                .map { title -> tools.first { it.title == title } },
            choose, en
        )
        Spacer(Modifier.height(12.dp))
        InfoPanel(if (en) "Precise PDF editing" else "Edição precisa de PDF", if (en) "Choose a tool, select an element and apply your changes." else "Escolha uma ferramenta, selecione um elemento e aplique as alterações.", Icons.Default.Tune)
        Spacer(Modifier.height(12.dp))
        PrimaryAction(if (en) "Choose document" else "Escolher documento", Icons.Default.FolderOpen, openPdf)
    }
}

@Composable
private fun ConverterPage(en: Boolean, fileName: String, openPdf: () -> Unit) {
    var selected by remember { mutableStateOf("Word (.docx)") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(if (en) "PDF Converter" else "Converter PDF", if (en) "Convert documents to the format you need" else "Converta documentos para vários formatos")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF102541)).border(1.dp, Color(0xFF294D78), RoundedCornerShape(15.dp)).clickable { openPdf() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFFF557E), modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(fileName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(if (en) "Tap to select another file" else "Toque para escolher outro ficheiro", color = MaxMuted, fontSize = 10.sp)
            }
            Icon(Icons.Default.Add, null, tint = MaxCyan)
        }
        Spacer(Modifier.height(18.dp))
        Text(if (en) "CONVERT TO" else "CONVERTER PARA", color = MaxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        listOf(
            Triple("Word (.docx)", Icons.Default.Description, Color(0xFF3189F5)),
            Triple("Excel (.xlsx)", Icons.Default.TableChart, Color(0xFF13B887)),
            Triple("PowerPoint (.pptx)", Icons.Default.Slideshow, Color(0xFFFF8050)),
            Triple(if (en) "Image (JPG / PNG)" else "Imagem (JPG / PNG)", Icons.Default.Image, Color(0xFFD64AB6)),
            Triple(if (en) "PDF (optimized)" else "PDF (otimizado)", Icons.Default.PictureAsPdf, Color(0xFF8D68F8))
        ).forEach { option ->
            val active = selected == option.first
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(13.dp)).background(if (active) Color(0xFF15385D) else Color(0xFF0E203B)).border(1.dp, if (active) MaxBlue else Color(0xFF203754), RoundedCornerShape(13.dp)).clickable { selected = option.first }.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(37.dp).clip(RoundedCornerShape(10.dp)).background(option.third.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) { Icon(option.second, null, tint = option.third) }
                Spacer(Modifier.width(12.dp))
                Text(option.first, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                RadioButton(active, { selected = option.first }, colors = RadioButtonDefaults.colors(selectedColor = MaxCyan))
            }
        }
        Spacer(Modifier.height(17.dp))
        PrimaryAction(if (en) "Convert file" else "Converter ficheiro", Icons.Default.SwapHoriz, {})
        Spacer(Modifier.height(12.dp))
        Text(if (en) "Conversion options" else "Opções de conversão", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(if (en) "Output quality • page range • preserve layout" else "Qualidade de saída • intervalo de páginas • manter layout", color = MaxMuted, fontSize = 11.sp)
    }
}

@Composable
private fun ScannerPage(en: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(if (en) "Scan & OCR" else "Digitalizar e OCR", if (en) "Turn paper documents into searchable PDFs" else "Transforme documentos físicos em PDFs pesquisáveis")
        Box(Modifier.fillMaxWidth().height(270.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF173556), Color(0xFF0B1A31)))).border(1.dp, Color(0xFF2B5B8E), RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(94.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF168BFF).copy(alpha = 0.19f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.DocumentScanner, null, tint = MaxCyan, modifier = Modifier.size(55.dp)) }
                Spacer(Modifier.height(15.dp))
                Text(if (en) "Ready to scan" else "Pronto para digitalizar", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(if (en) "Place a document inside the frame" else "Coloque o documento dentro da moldura", color = MaxMuted, fontSize = 11.sp)
                Spacer(Modifier.height(15.dp))
                Box(Modifier.width(190.dp).height(2.dp).background(MaxCyan))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(if (en) "SCAN MODE" else "MODO DE DIGITALIZAÇÃO", color = MaxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(if (en) "Document" else "Documento", if (en) "Book" else "Livro", if (en) "Card" else "Cartão", if (en) "Photo" else "Foto").forEachIndexed { index, label ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (index == 0) Color(0xFF075FC0) else Color(0xFF152945)).padding(vertical = 11.dp), contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 10.sp) }
            }
        }
        Spacer(Modifier.height(17.dp))
        PrimaryAction(if (en) "Start scan" else "Iniciar digitalização", Icons.Default.CameraAlt, {})
        Spacer(Modifier.height(10.dp))
        SecondaryAction(if (en) "Import an image" else "Importar uma imagem", Icons.Default.Image, {})
        Spacer(Modifier.height(15.dp))
        InfoPanel("OCR", if (en) "Recognize text and make scanned pages searchable." else "Reconheça o texto e torne as páginas digitalizadas pesquisáveis.", Icons.Default.TextFields)
    }
}

@Composable
private fun ProtectPage(en: Boolean) {
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(if (en) "Protect PDF" else "Proteger PDF", if (en) "Control access to your documents" else "Controle o acesso aos seus documentos")
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF112541)).padding(4.dp)) {
            listOf(if (en) "Password" else "Senha", if (en) "Permissions" else "Permissões", if (en) "Signature" else "Assinatura").forEachIndexed { i, label ->
                Box(Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (mode == i) Color(0xFF0875D9) else Color.Transparent).clickable { mode = i }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) { Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(26.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(Modifier.size(100.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Color(0xFF177DFF).copy(alpha = 0.5f), Color(0xFF122F62)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.Lock, null, tint = Color.White, modifier = Modifier.size(50.dp)) }
        }
        Spacer(Modifier.height(18.dp))
        Text(if (en) "Set a password" else "Definir uma senha", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(if (en) "Protect your PDF with a secure password." else "Proteja o seu PDF com uma senha de acesso.", color = MaxMuted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (en) "Password" else "Senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, colors = darkFieldColors())
        Spacer(Modifier.height(9.dp))
        OutlinedTextField(confirm, { confirm = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (en) "Confirm password" else "Confirmar senha") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, colors = darkFieldColors())
        Spacer(Modifier.height(13.dp))
        Text(if (en) "Password strength" else "Força da senha", color = MaxMuted, fontSize = 10.sp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(5) { i -> Box(Modifier.weight(1f).height(5.dp).clip(CircleShape).background(if (password.length >= (i + 1) * 2) Color(0xFF18C98A) else Color(0xFF263C58))) }
        }
        Spacer(Modifier.height(22.dp))
        PrimaryAction(if (en) "Protect PDF" else "Proteger PDF", Icons.Default.Security, {})
        Spacer(Modifier.height(12.dp))
        InfoPanel(if (en) "Document protection" else "Proteção do documento", if (en) "Choose a PDF first. Encryption is performed by the document engine." else "Escolha primeiro um PDF. A encriptação será realizada pelo motor de documentos.", Icons.Default.Shield)
    }
}

@Composable
private fun SettingsPage(en: Boolean, changeLanguage: () -> Unit) {
    val context = LocalContext.current
    val storageSummary = remember(en) {
        runCatching {
            val stats = android.os.StatFs(android.os.Environment.getExternalStorageDirectory().absolutePath)
            val free = android.text.format.Formatter.formatShortFileSize(context, stats.availableBytes)
            val total = android.text.format.Formatter.formatShortFileSize(context, stats.totalBytes)
            if (en) "$free free of $total" else "$free livres de $total"
        }.getOrElse { if (en) "Storage information unavailable" else "Informação de armazenamento indisponível" }
    }

    fun openAndroidAppSettings() {
        runCatching {
            context.startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + context.packageName)
                )
            )
        }
    }

    fun openSupport() {
        runCatching {
            context.startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/nexauren1/PDF-Max-PDF-Editor-Reader/issues")
                )
            )
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(
            if (en) "Settings" else "Configurações",
            if (en) "Manage your PDF Max preferences and device access" else "Gira as preferências do PDF Max e o acesso ao dispositivo"
        )
        SettingsGroup(if (en) "PREFERENCES" else "PREFERÊNCIAS")
        SettingRow(
            Icons.Default.Palette,
            if (en) "Appearance" else "Aparência",
            if (en) "Premium dark interface" else "Interface escura premium"
        )
        SettingRow(
            Icons.Default.Language,
            if (en) "Language" else "Idioma",
            if (en) "English / Português" else "Português / English",
            changeLanguage
        )
        SettingRow(
            Icons.Default.Notifications,
            if (en) "Notifications" else "Notificações",
            if (en) "No push notifications are configured in this release." else "Esta versão não configura notificações push."
        )
        SettingsGroup(if (en) "STORAGE & PRIVACY" else "ARMAZENAMENTO E PRIVACIDADE")
        SettingRow(
            Icons.Default.Storage,
            if (en) "Device storage" else "Armazenamento do dispositivo",
            storageSummary
        )
        SettingRow(
            Icons.Default.Security,
            if (en) "App permissions" else "Permissões da aplicação",
            if (en) "Review document and camera access in Android settings" else "Reveja o acesso a documentos e à câmara nas definições Android",
            ::openAndroidAppSettings
        )
        SettingRow(
            Icons.Default.Cloud,
            if (en) "Cloud sync" else "Sincronização na nuvem",
            if (en) "Not available in this release" else "Ainda não disponível nesta versão"
        )
        SettingsGroup(if (en) "SUPPORT & ABOUT" else "SUPORTE E SOBRE")
        SettingRow(
            Icons.Default.Help,
            if (en) "Help & support" else "Ajuda e suporte",
            if (en) "Open the PDF Max issue tracker" else "Abrir o suporte do PDF Max no GitHub",
            ::openSupport
        )
        SettingRow(
            Icons.Default.Info,
            if (en) "About PDF Max" else "Sobre o PDF Max",
            "PDF Max 1.2.6 • Android 8.0+"
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "PDF Max – PDF Editor & Reader",
            color = MaxMuted, fontSize = 10.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun ToolDetailPage(tool: ToolSpec, en: Boolean, openPdf: () -> Unit) {
    var choice by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(tool.title, tool.subtitle)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.linearGradient(listOf(tool.color.copy(alpha = 0.30f), Color(0xFF10213B)))).border(1.dp, tool.color.copy(alpha = 0.55f), RoundedCornerShape(18.dp)).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(59.dp).clip(RoundedCornerShape(16.dp)).background(tool.color), contentAlignment = Alignment.Center) { Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(33.dp)) }
            Spacer(Modifier.width(13.dp))
            Column {
                Text(tool.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(if (en) "Configure your options" else "Configure as suas opções", color = MaxMuted, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle(if (en) "1. Select a document" else "1. Selecionar documento", if (en) "Browse" else "Procurar", openPdf)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF10233F)).border(1.dp, Color(0xFF29466C), RoundedCornerShape(13.dp)).clickable { openPdf() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PictureAsPdf, null, tint = Color(0xFFFF5278), modifier = Modifier.size(29.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (en) "Choose PDF file" else "Escolher ficheiro PDF", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(if (en) "Select a file from your device" else "Selecione um ficheiro do dispositivo", color = MaxMuted, fontSize = 10.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MaxMuted)
        }
        Spacer(Modifier.height(20.dp))
        SectionTitle(if (en) "2. Options" else "2. Opções", "") {}
        val options = when (tool.title) {
            "Comprimir PDF" -> listOf("Máxima qualidade", "Equilibrado", "Tamanho mínimo")
            "Dividir PDF" -> listOf("Todas as páginas", "Intervalo de páginas", "Páginas selecionadas")
            "Marca de água" -> listOf("Texto", "Imagem", "Personalizada")
            "Rodar páginas" -> listOf("90°", "180°", "270°")
            else -> listOf(if (en) "Recommended" else "Recomendado", if (en) "Advanced options" else "Opções avançadas", if (en) "Custom settings" else "Configuração personalizada")
        }
        options.forEachIndexed { i, label ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(11.dp)).background(Color(0xFF10213A)).clickable { choice = i }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                RadioButton(choice == i, { choice = i }, colors = RadioButtonDefaults.colors(selectedColor = MaxCyan))
            }
        }
        Spacer(Modifier.height(18.dp))
        PrimaryAction(if (en) "Continue" else "Continuar", Icons.Default.ArrowForward, {})
        Spacer(Modifier.height(12.dp))
        Text(if (en) "Processing options will be activated as this tool is implemented." else "As opções de processamento serão ativadas à medida que esta ferramenta for implementada.", color = MaxMuted, fontSize = 10.sp)
    }
}

@Composable
private fun SideMenu(current: Page, en: Boolean, navigate: (Page) -> Unit, language: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_pdfmax),
                contentDescription = "PDF Max",
                modifier = Modifier.size(43.dp).clip(RoundedCornerShape(13.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text("PDF Max", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("PDF Editor & Reader", color = MaxMuted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(23.dp))
        Text(if (en) "WORKSPACE" else "ESPAÇO DE TRABALHO", color = Color(0xFF7186A5), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 7.dp))
        DrawerItem(Icons.Default.Home, if (en) "Home" else "Início", current == Page.HOME) { navigate(Page.HOME) }
        DrawerItem(Icons.Default.FolderOpen, if (en) "My files" else "Meus Arquivos", current == Page.FILES) { navigate(Page.FILES) }
        DrawerItem(Icons.Default.GridView, if (en) "Tools" else "Ferramentas", current == Page.TOOLS) { navigate(Page.TOOLS) }
        DrawerItem(Icons.Default.Star, if (en) "Favorites" else "Favoritos", current == Page.FAVORITES) { navigate(Page.FAVORITES) }
        DrawerItem(Icons.Default.History, if (en) "Recent" else "Recentes", current == Page.RECENT) { navigate(Page.RECENT) }
        DrawerItem(Icons.Default.Delete, if (en) "Trash" else "Lixeira", current == Page.TRASH) { navigate(Page.TRASH) }
        Spacer(Modifier.height(17.dp))
        Text(if (en) "PREFERENCES" else "PREFERÊNCIAS", color = Color(0xFF7186A5), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 10.dp, bottom = 7.dp))
        DrawerItem(Icons.Default.Settings, if (en) "Settings" else "Configurações", current == Page.SETTINGS) { navigate(Page.SETTINGS) }
        DrawerItem(Icons.Default.Help, if (en) "Help & support" else "Ajuda e suporte", false) { navigate(Page.SETTINGS) }
        DrawerItem(Icons.Default.Info, if (en) "About" else "Sobre", false) { navigate(Page.SETTINGS) }
        Spacer(Modifier.weight(1f))
        Divider(color = Color(0xFF20334F))
        Row(Modifier.fillMaxWidth().clickable { language() }.padding(horizontal = 10.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Language, null, tint = MaxCyan)
            Spacer(Modifier.width(12.dp))
            Text(if (en) "English" else "Português", color = Color.White, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("PT / EN", color = MaxMuted, fontSize = 10.sp)
        }
        Text("PDF Max  •  v1.2.6", color = MaxMuted, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun DrawerItem(icon: ImageVector, label: String, selected: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).clip(RoundedCornerShape(10.dp)).background(if (selected) Color(0xFF123F78) else Color.Transparent).clickable(onClick = click).padding(horizontal = 11.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) Color(0xFF58B9FF) else Color(0xFFB1C2DC), modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(13.dp))
        Text(label, color = if (selected) Color.White else Color(0xFFD9E4F5), fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun BottomBar(modifier: Modifier, current: Page, en: Boolean, home: () -> Unit, files: () -> Unit, add: () -> Unit, favoritesClick: () -> Unit, menu: () -> Unit) {
    Row(modifier.background(Color(0xFF071329)).border(BorderStroke(1.dp, Color(0xFF1C3557))).navigationBarsPadding().height(66.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        BottomItem(Modifier.weight(1f), Icons.Default.Home, if (en) "Home" else "Início", current == Page.HOME, home)
        BottomItem(Modifier.weight(1f), Icons.Default.FolderOpen, if (en) "Files" else "Arquivos", current == Page.FILES, files)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF20B7FF), Color(0xFF8E36FF), Color(0xFF275DFF)))).border(1.dp, Color(0xFF67D9FF), CircleShape).clickable(onClick = add), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(29.dp)) }
        }
        BottomItem(Modifier.weight(1f), Icons.Default.Star, if (en) "Favorites" else "Favoritos", current == Page.FAVORITES, favoritesClick)
        BottomItem(Modifier.weight(1f), Icons.Default.Menu, if (en) "More" else "Mais", false, menu)
    }
}

@Composable
private fun BottomItem(modifier: Modifier, icon: ImageVector, label: String, selected: Boolean, click: () -> Unit) {
    Column(modifier.fillMaxHeight().clickable(onClick = click), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, tint = if (selected) MaxCyan else Color(0xFF8092AE), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (selected) MaxCyan else Color(0xFF8092AE), fontSize = 9.sp)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(3.dp))
    Text(subtitle, color = MaxMuted, fontSize = 11.sp)
    Spacer(Modifier.height(17.dp))
}

@Composable
private fun SearchBar(hint: String, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF172A49)).clickable(onClick = click).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Search, null, tint = MaxMuted, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(8.dp))
        Text(hint, color = MaxMuted, fontSize = 12.sp)
    }
}

@Composable
private fun PrimaryAction(text: String, icon: ImageVector, click: () -> Unit, modifier: Modifier = Modifier) {
    Button(onClick = click, modifier = modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FF1)), contentPadding = PaddingValues(horizontal = 14.dp)) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun SecondaryAction(text: String, icon: ImageVector, click: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(onClick = click, modifier = modifier.fillMaxWidth().height(49.dp), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF2B5C90)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
        Icon(icon, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(7.dp))
        Text(text, fontSize = 11.sp)
    }
}

@Composable
private fun InfoPanel(title: String, text: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF112541)).border(1.dp, Color(0xFF294667), RoundedCornerShape(14.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaxCyan, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(11.dp))
        Column {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text, color = MaxMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
    }
}

@Composable
private fun SettingsGroup(text: String) {
    Text(text, color = MaxMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 17.dp, bottom = 8.dp))
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, detail: String, click: (() -> Unit)? = null) {
    val rowModifier = if (click != null) {
        Modifier.clickable { click() }
    } else {
        Modifier
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .then(rowModifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MaxCyan, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaxMuted, fontSize = 10.sp, lineHeight = 14.sp)
        }
        if (click != null) Icon(Icons.Default.ChevronRight, null, tint = MaxMuted)
    }
    Divider(color = Color(0xFF1C2E49))
}

@Composable
private fun darkFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = MaxBlue, unfocusedBorderColor = Color(0xFF304766),
    focusedLabelColor = MaxCyan, unfocusedLabelColor = MaxMuted,
    cursorColor = MaxCyan, focusedContainerColor = Color(0xFF0D1D35),
    unfocusedContainerColor = Color(0xFF0D1D35)
)

internal fun localizedToolTitle(title: String, english: Boolean): String {
    if (!english) return title
    return when (title) {
        "Criar PDF" -> "Create PDF"; "Editar PDF" -> "Edit PDF"; "Converter PDF" -> "Convert PDF"
        "Comprimir PDF" -> "Compress PDF"; "Juntar PDF" -> "Merge PDFs"; "Dividir PDF" -> "Split PDF"
        "Proteger PDF" -> "Protect PDF"; "Assinar PDF" -> "Sign PDF"; "Digitalizar e OCR" -> "Scan & OCR"
        "Extrair imagens" -> "Extract images"; "Adicionar texto" -> "Add text"; "Marca de água" -> "Watermark"
        "Numerar páginas" -> "Page numbers"; "Rodar páginas" -> "Rotate pages"; "Comparar PDFs" -> "Compare PDFs"
        "Traduzir PDF" -> "Translate PDF"; "Preencher formulários" -> "Fill forms"; "Reparar PDF" -> "Repair PDF"
        "PDF para imagem" -> "PDF to image"; "Imagem para PDF" -> "Image to PDF"; "Organizar páginas" -> "Organize pages"
        "Imprimir PDF" -> "Print PDF"; "Partilhar PDF" -> "Share PDF"; "Ver propriedades" -> "File properties"
        "Remover páginas" -> "Delete pages"; "Extrair texto PDF" -> "Extract PDF text"; "Criar ZIP" -> "Create ZIP"
        "Detetar duplicados" -> "Find duplicates"; "Extrair páginas" -> "Extract pages"
        "Adicionar página em branco" -> "Add blank page"; "Editar metadados" -> "Edit metadata"
        "Achatar formulários" -> "Flatten forms"; "Remover metadados" -> "Remove metadata"
        "Relatório do PDF" -> "PDF report"
        else -> title
    }
}
internal fun localizedToolSubtitle(title: String, english: Boolean): String {
    if (!english) return if (title == "Criar PDF") "Laboratório de escrita e layout" else tools.firstOrNull { it.title == title }?.subtitle.orEmpty()
    return when (title) {
        "Criar PDF" -> "Writing and layout workspace"; "Editar PDF" -> "Add text overlays to PDF pages"
        "Converter PDF" -> "Word, Excel and images"; "Comprimir PDF" -> "Rewrite PDF structure; size may vary"
        "Juntar PDF" -> "Combine documents"; "Dividir PDF" -> "Split by page range"
        "Proteger PDF" -> "Password and permissions"; "Assinar PDF" -> "Handwritten visual signature"
        "Digitalizar e OCR" -> "Scan documents to PDF"; "Extrair imagens" -> "Save embedded images"
        "Adicionar texto" -> "Insert text"; "Marca de água" -> "Text or image watermark"
        "Numerar páginas" -> "Headers and footers"; "Rodar páginas" -> "Change page orientation"
        "Comparar PDFs" -> "Compare document content"; "Traduzir PDF" -> "Translate document text"
        "Preencher formulários" -> "Fill interactive fields"; "Reparar PDF" -> "Attempt to recover readable PDFs"
        "PDF para imagem" -> "Export pages as images"; "Imagem para PDF" -> "Combine JPG and PNG files"
        "Organizar páginas" -> "Reorder document pages"; "Imprimir PDF" -> "Prepare for printing"
        "Partilhar PDF" -> "Send your document"; "Ver propriedades" -> "Document information"
        "Remover páginas" -> "Delete selected pages"; "Extrair texto PDF" -> "Save text as TXT"
        "Criar ZIP" -> "Archive multiple files"; "Detetar duplicados" -> "Find exact copies"
        "Extrair páginas" -> "Create a PDF from selected pages"; "Adicionar página em branco" -> "Insert a new page"
        "Editar metadados" -> "Title, author and keywords"
        "Achatar formulários" -> "Make existing form fields read-only page content"
        "Remover metadados" -> "Clear standard document info and catalog XMP metadata"
        "Relatório do PDF" -> "Pages, word count and extractable text"
        else -> tools.firstOrNull { it.title == title }?.subtitle.orEmpty()
    }
}
