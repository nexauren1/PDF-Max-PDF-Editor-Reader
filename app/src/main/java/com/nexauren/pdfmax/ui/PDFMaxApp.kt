package com.nexauren.pdfmax.ui

import android.net.Uri
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nexauren.pdfmax.ui.theme.*
import com.nexauren.pdfmax.data.DocumentKind
import com.nexauren.pdfmax.data.DocumentScanner
import com.nexauren.pdfmax.data.IndexedDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

internal enum class Page { HOME, FILES, TOOLS, READER, OFFICE, EDITOR, CONVERTER, SCANNER, PROTECT, SETTINGS, TOOL }
internal data class ToolSpec(val title: String, val subtitle: String, val icon: ImageVector, val color: Color, val page: Page = Page.TOOL)

private val tools = listOf(
    ToolSpec("Editar PDF", "Texto, imagens e notas", Icons.Default.Edit, Color(0xFF9257FF), Page.EDITOR),
    ToolSpec("Converter PDF", "Word, Excel e imagem", Icons.Default.SwapHoriz, Color(0xFF13B8A6), Page.CONVERTER),
    ToolSpec("Comprimir PDF", "Reduzir o tamanho", Icons.Default.Compress, Color(0xFF08A9DB)),
    ToolSpec("Juntar PDF", "Combinar documentos", Icons.Default.MergeType, Color(0xFFFF8A35)),
    ToolSpec("Dividir PDF", "Separar por páginas", Icons.Default.CallSplit, Color(0xFF4285F4)),
    ToolSpec("Proteger PDF", "Senha e permissões", Icons.Default.Security, Color(0xFFE84B70), Page.PROTECT),
    ToolSpec("Assinar PDF", "Assinatura eletrónica", Icons.Default.Draw, Color(0xFF9A59FF)),
    ToolSpec("Digitalizar e OCR", "Documento para PDF", Icons.Default.QrCodeScanner, Color(0xFF13B7D0), Page.SCANNER),
    ToolSpec("Extrair imagens", "Guardar imagens", Icons.Default.Image, Color(0xFF18A5E8)),
    ToolSpec("Adicionar texto", "Inserir texto", Icons.Default.TextFields, Color(0xFF17B7D3), Page.EDITOR),
    ToolSpec("Marca de água", "Texto ou imagem", Icons.Default.WaterDrop, Color(0xFF527DFF)),
    ToolSpec("Numerar páginas", "Cabeçalho e rodapé", Icons.Default.FormatListNumbered, Color(0xFF00B99A)),
    ToolSpec("Rodar páginas", "Orientação do PDF", Icons.Default.RotateRight, Color(0xFF19B5A2)),
    ToolSpec("Comparar PDFs", "Ver diferenças", Icons.Default.CompareArrows, Color(0xFFAA63F8)),
    ToolSpec("Traduzir PDF", "Vários idiomas", Icons.Default.Translate, Color(0xFF3989F4)),
    ToolSpec("Criar PDF", "Documento em branco", Icons.Default.Description, Color(0xFFE65A82)),
    ToolSpec("Preencher formulários", "Campos e formulários", Icons.Default.EditNote, Color(0xFF4A8DFF)),
    ToolSpec("Reparar PDF", "Recuperar ficheiro", Icons.Default.Refresh, Color(0xFF15B9A3)),
    ToolSpec("PDF para imagem", "JPG e PNG", Icons.Default.Photo, Color(0xFFB05BF6)),
    ToolSpec("Imagem para PDF", "JPG, PNG e mais", Icons.Default.Collections, Color(0xFF12A9D8)),
    ToolSpec("Organizar páginas", "Reordenar e eliminar", Icons.Default.GridView, Color(0xFF168DFF)),
    ToolSpec("Imprimir PDF", "Preparar impressão", Icons.Default.Print, Color(0xFF7C70F5)),
    ToolSpec("Partilhar PDF", "Enviar documento", Icons.Default.Share, Color(0xFF16B89B)),
    ToolSpec("Ver propriedades", "Informações do ficheiro", Icons.Default.Info, Color(0xFF4F8FEA))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDFMaxApp(initialPdf: Uri? = null) {
    var screen by remember { mutableStateOf(if (initialPdf == null) Page.HOME else Page.READER) }
    var currentTool by remember { mutableStateOf(tools[0]) }
    var pdfUri by remember { mutableStateOf(initialPdf) }
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var pdfName by remember { mutableStateOf("Relatório_Final.pdf") }
    var english by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    fun go(target: Page) { screen = target; scope.launch { drawer.close() } }
    fun openTool(tool: ToolSpec) { currentTool = tool; screen = tool.page }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = Color(0xB8000616),
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(302.dp),
                drawerContainerColor = Color(0xFF09172D),
                drawerContentColor = MaxText
            ) {
                SideMenu(screen, english, { go(it) }, { english = !english })
            }
        }
    ) {
        Scaffold(
            containerColor = MaxNavy,
            bottomBar = {
                BottomBar(
                    Modifier.fillMaxWidth(), screen, english,
                    { go(Page.HOME) }, { go(Page.FILES) },
                    { go(Page.FILES) },
                    { go(Page.TOOLS) }, { scope.launch { drawer.open() } }
                )
            }
        ) { insets ->
            Column(
                Modifier.fillMaxSize().padding(insets)
                    .background(Brush.verticalGradient(listOf(Color(0xFF071329), Color(0xFF08162B), Color(0xFF050E20))))
            ) {
                TopBar(screen, english, { scope.launch { drawer.open() } }, { go(Page.HOME) }, { go(Page.SETTINGS) })
                when (screen) {
                    Page.HOME -> Dashboard(english, { go(it) }, { openTool(it) }, { go(Page.FILES) }) { file ->
    selectedFile = file
    pdfName = file.name
    pdfUri = Uri.fromFile(file)
    screen = if (file.extension.equals("pdf", true)) Page.READER else Page.OFFICE
}
                    Page.FILES -> DocumentLibraryScreen(english) { file ->
                        selectedFile = file
                        pdfName = file.name
                        pdfUri = Uri.fromFile(file)
                        screen = if (file.extension.equals("pdf", true)) Page.READER else Page.OFFICE
                    }
                    Page.OFFICE -> OfficePreviewScreen(selectedFile, english) { go(Page.FILES) }
                    Page.TOOLS -> ToolsPage(english, { openTool(it) })
                    Page.READER -> ReaderPage(english, pdfName, pdfUri, { go(Page.FILES) }, { go(Page.EDITOR) }, { openTool(it) })
                    Page.EDITOR -> ToolWorkspaceScreen(tools[0], english)
                    Page.CONVERTER -> ToolWorkspaceScreen(tools[1], english)
                    Page.SCANNER -> ToolWorkspaceScreen(tools[7], english)
                    Page.PROTECT -> ToolWorkspaceScreen(tools[5], english)
                    Page.SETTINGS -> SettingsPage(english, { english = !english })
                    Page.TOOL -> ToolWorkspaceScreen(currentTool, english)
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
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFFF3469), Color(0xFF754CFF), Color(0xFF128DFF)))),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("PDF Max", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Text(if (english) "PDF Editor & Reader" else "Editor e Leitor de PDF", fontSize = 10.sp, color = MaxMuted)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = Color(0xFFC1D2EE)) }
        IconButton(onClick = {}) { Icon(Icons.Default.NotificationsNone, null, tint = Color(0xFFC1D2EE)) }
    }
}

@Composable
private fun Dashboard(en: Boolean, navigate: (Page) -> Unit, choose: (ToolSpec) -> Unit, openPdf: () -> Unit, openFile: (File) -> Unit) {
    val context = LocalContext.current
    var recentDocuments by remember { mutableStateOf<List<IndexedDocument>>(emptyList()) }
    LaunchedEffect(Unit) {
        if (DocumentScanner.hasAccess(context)) {
            recentDocuments = withContext(Dispatchers.IO) { DocumentScanner.scan(context).take(5) }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 17.dp, vertical = 5.dp)) {
        Text(if (en) "Hello, welcome back" else "Olá, bem-vindo!", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(if (en) "Manage your documents and explore your tools." else "Gere os seus documentos e aproveite todas as ferramentas.", color = MaxMuted, fontSize = 12.sp)
        Spacer(Modifier.height(16.dp))
        SearchBar(if (en) "Search files and folders…" else "Pesquisar arquivos e pastas…") { navigate(Page.FILES) }
        Spacer(Modifier.height(15.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF4225A5), Color(0xFF142D75), Color(0xFF0B7197))))
                .border(1.dp, Color(0xFF466DFF), RoundedCornerShape(20.dp)).padding(17.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFBFA8FF), modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (en) "YOUR PDF ASSISTANT" else "O SEU ASSISTENTE DE PDF", color = Color(0xFFD4C8FF), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (en) "Everything you need, in one place" else "Todas as ferramentas num só lugar", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(if (en) "Read, edit, convert and protect files." else "Leia, edite, converta e proteja os seus documentos.", color = Color(0xFFD6E2FF), fontSize = 11.sp)
                    Spacer(Modifier.height(11.dp))
                    Row(Modifier.clip(CircleShape).background(Color(0xFF168BFF)).clickable { navigate(Page.TOOLS) }.padding(horizontal = 13.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(if (en) "Explore tools" else "Explorar ferramentas", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(5.dp))
                        Icon(Icons.Default.ArrowForward, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Box(Modifier.size(80.dp).clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF9562FF), Color(0xFF167EFF)))),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(50.dp)) }
            }
        }
        Spacer(Modifier.height(18.dp))
        SectionTitle(if (en) "Quick tools" else "Ferramentas rápidas", if (en) "See all" else "Ver tudo") { navigate(Page.TOOLS) }
        ToolGrid(listOf(tools[0], tools[1], tools[2], tools[3], tools[5], tools[7]), choose)
        Spacer(Modifier.height(18.dp))
        SectionTitle(if (en) "Recent files" else "Arquivos recentes", if (en) "View all" else "Ver todos") { navigate(Page.FILES) }
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
private fun ToolGrid(list: List<ToolSpec>, onChoose: (ToolSpec) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        list.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEach { ToolTile(it, Modifier.weight(1f)) { onChoose(it) } }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ToolTile(tool: ToolSpec, modifier: Modifier = Modifier, click: () -> Unit) {
    Column(
        modifier.height(92.dp).clip(RoundedCornerShape(15.dp))
            .background(Brush.linearGradient(listOf(tool.color.copy(alpha = 0.25f), Color(0xFF0D1B33), Color(0xFF101A31))))
            .border(1.dp, tool.color.copy(alpha = 0.48f), RoundedCornerShape(15.dp))
            .clickable(onClick = click).padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(37.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(tool.color, tool.color.copy(alpha = 0.65f)))), contentAlignment = Alignment.Center) {
            Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(tool.title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 12.sp, overflow = TextOverflow.Ellipsis)
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

@Composable
private fun ToolsPage(en: Boolean, choose: (ToolSpec) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(if (en) "All tools" else "Todas as ferramentas", if (en) "A complete workspace for PDF documents" else "Um espaço completo para trabalhar com documentos PDF")
        SearchBar(if (en) "Find a tool…" else "Procurar uma ferramenta…") {}
        Spacer(Modifier.height(14.dp))
        SectionTitle(if (en) "PDF tools" else "Ferramentas PDF", "24") {}
        ToolGrid(tools, choose)
        Spacer(Modifier.height(18.dp))
        InfoPanel(if (en) "Powerful document workflow" else "Fluxo de documentos avançado", if (en) "Open a tool to configure its options and select a document." else "Abra uma ferramenta para configurar as opções e selecionar um documento.", Icons.Default.AutoAwesome)
    }
}

@Composable
private fun ReaderPage(en: Boolean, fileName: String, uri: Uri?, openPdf: () -> Unit, edit: () -> Unit, choose: (ToolSpec) -> Unit) {
    var pageNumber by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 15.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (uri == null) "Relatório_Final.pdf" else fileName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (en) "$pageNumber of 24 pages" else "$pageNumber de 24 páginas", color = MaxMuted, fontSize = 10.sp)
            }
            IconButton(onClick = openPdf) { Icon(Icons.Default.FolderOpen, null, tint = MaxCyan) }
            IconButton(onClick = {}) { Icon(Icons.Default.Search, null, tint = MaxText) }
            IconButton(onClick = {}) { Icon(Icons.Default.BookmarkBorder, null, tint = MaxText) }
            IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null, tint = MaxText) }
        }
        PdfPagePreview(uri, Modifier.padding(top = 3.dp))
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF112441)).border(1.dp, Color(0xFF294D78), RoundedCornerShape(15.dp)).padding(vertical = 11.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ReaderAction(Icons.Default.ZoomIn, "Zoom")
            ReaderAction(Icons.Default.Search, if (en) "Search" else "Pesquisar")
            ReaderAction(Icons.Default.BookmarkBorder, if (en) "Bookmarks" else "Marcadores")
            ReaderAction(Icons.Default.DarkMode, if (en) "Night" else "Noturno")
            ReaderAction(Icons.Default.Share, if (en) "Share" else "Partilhar")
        }
        Spacer(Modifier.height(12.dp))
        SectionTitle(if (en) "Pages" else "Páginas", if (en) "Organize" else "Organizar") { choose(tools[20]) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { n ->
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).border(if (pageNumber == n) 2.dp else 1.dp, if (pageNumber == n) MaxBlue else Color(0xFF263C5D), RoundedCornerShape(8.dp)).background(Color(0xFF172943)).clickable { pageNumber = n }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Column(Modifier.fillMaxWidth().height(66.dp).background(Color(0xFFF3F6FB), RoundedCornerShape(3.dp)).padding(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.fillMaxWidth().height(13.dp).background(Color(0xFF2166AB)))
                        repeat(4) { Box(Modifier.fillMaxWidth(if (it == 3) 0.7f else 1f).height(3.dp).background(Color(0xFFD6DEEA))) }
                    }
                    Text("$n", color = Color.White, fontSize = 10.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryAction(if (en) "Edit PDF" else "Editar PDF", Icons.Default.Edit, edit, Modifier.weight(1f))
            SecondaryAction(if (en) "More tools" else "Mais ferramentas", Icons.Default.GridView, { choose(tools[20]) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ReaderAction(icon: ImageVector, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(min = 43.dp)) {
        Icon(icon, null, tint = Color(0xFFE4EEFF), modifier = Modifier.size(19.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color(0xFFD6E2F7), fontSize = 9.sp)
    }
}

@Composable
private fun EditorPage(en: Boolean, uri: Uri?, openPdf: () -> Unit, choose: (ToolSpec) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(15.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (en) "Edit PDF" else "Editar PDF", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = {}) { Icon(Icons.Default.Undo, null, tint = MaxText) }
            IconButton(onClick = {}) { Icon(Icons.Default.Redo, null, tint = MaxText) }
            IconButton(onClick = {}) { Icon(Icons.Default.Check, null, tint = Color(0xFF55D6A4)) }
        }
        PdfPagePreview(uri)
        Spacer(Modifier.height(10.dp))
        Text(if (en) "EDITING TOOLS" else "FERRAMENTAS DE EDIÇÃO", color = MaxMuted, fontWeight = FontWeight.Bold, fontSize = 10.sp)
        Spacer(Modifier.height(8.dp))
        ToolGrid(listOf(tools[9], tools[0], tools[6], tools[10], tools[16], tools[12]), choose)
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)) {
        PageTitle(if (en) "Settings" else "Configurações", if (en) "Personalize your PDF Max experience" else "Personalize a sua experiência no PDF Max")
        SettingsGroup(if (en) "PREFERENCES" else "PREFERÊNCIAS")
        SettingRow(Icons.Default.Palette, if (en) "Appearance" else "Aparência", if (en) "Dark mode" else "Modo escuro", true)
        SettingRow(Icons.Default.Language, if (en) "Language" else "Idioma", if (en) "English / Português" else "Português / English", false, changeLanguage)
        SettingRow(Icons.Default.Notifications, if (en) "Notifications" else "Notificações", if (en) "App alerts" else "Avisos da aplicação", true)
        SettingsGroup(if (en) "STORAGE & PRIVACY" else "ARMAZENAMENTO E PRIVACIDADE")
        SettingRow(Icons.Default.Storage, if (en) "Storage" else "Armazenamento", "12.4 GB / 64 GB", false)
        SettingRow(Icons.Default.Security, if (en) "Privacy" else "Privacidade", if (en) "Permissions and data" else "Permissões e dados", false)
        SettingRow(Icons.Default.Cloud, if (en) "Cloud sync" else "Sincronização na nuvem", if (en) "Not configured" else "Não configurada", false)
        SettingsGroup(if (en) "ABOUT" else "SOBRE")
        SettingRow(Icons.Default.Help, if (en) "Help & support" else "Ajuda e suporte", "FAQ • Contact", false)
        SettingRow(Icons.Default.Info, if (en) "About PDF Max" else "Sobre o PDF Max", "Version 1.0.0", false)
        Spacer(Modifier.height(18.dp))
        Text("PDF Max – PDF Editor & Reader", color = MaxMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
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
            Box(Modifier.size(43.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF3475), Color(0xFF6E4BFF), Color(0xFF128CFF)))), contentAlignment = Alignment.Center) { Icon(Icons.Default.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(29.dp)) }
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
        DrawerItem(Icons.Default.Star, if (en) "Favorites" else "Favoritos", false) { navigate(Page.FILES) }
        DrawerItem(Icons.Default.History, if (en) "Recent" else "Recentes", false) { navigate(Page.FILES) }
        DrawerItem(Icons.Default.Delete, if (en) "Trash" else "Lixeira", false) { navigate(Page.FILES) }
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
        Text("PDF Max  •  v1.0.0", color = MaxMuted, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
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
private fun BottomBar(modifier: Modifier, current: Page, en: Boolean, home: () -> Unit, files: () -> Unit, add: () -> Unit, toolsClick: () -> Unit, menu: () -> Unit) {
    Row(modifier.background(Color(0xFF071329)).border(BorderStroke(1.dp, Color(0xFF1C3557))).navigationBarsPadding().height(66.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
        BottomItem(Modifier.weight(1f), Icons.Default.Home, if (en) "Home" else "Início", current == Page.HOME, home)
        BottomItem(Modifier.weight(1f), Icons.Default.FolderOpen, if (en) "Files" else "Arquivos", current == Page.FILES, files)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF20B7FF), Color(0xFF275DFF)))).clickable(onClick = add), contentAlignment = Alignment.Center) { Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(29.dp)) }
        }
        BottomItem(Modifier.weight(1f), Icons.Default.GridView, if (en) "Tools" else "Ferramentas", current == Page.TOOLS, toolsClick)
        BottomItem(Modifier.weight(1f), Icons.Default.Menu, "Menu", false, menu)
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
private fun SettingRow(icon: ImageVector, title: String, detail: String, toggle: Boolean, click: () -> Unit = {}) {
    var checked by remember { mutableStateOf(true) }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { if (toggle) checked = !checked else click() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaxCyan, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaxMuted, fontSize = 10.sp)
        }
        if (toggle) Switch(checked, { checked = it }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = MaxBlue))
        else Icon(Icons.Default.ChevronRight, null, tint = MaxMuted)
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
