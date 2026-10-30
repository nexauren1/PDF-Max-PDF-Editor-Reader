package com.nexauren.pdfmax

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SplitScreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Blue = Color(0xFF258BFF)
private val Cyan = Color(0xFF22D3EE)
private val Violet = Color(0xFF8057FF)
private val Pink = Color(0xFFFF4F9A)
private val Green = Color(0xFF17C987)
private val Orange = Color(0xFFFF9B45)
private val Muted = Color(0xFF8FA6C5)

private fun tr(en: Boolean, pt: String, english: String) = if (en) english else pt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var dark by rememberSaveable { mutableStateOf(true) }
            PdfMaxTheme(dark) { PdfMaxApp(dark, { dark = it }) }
        }
    }
}

@Composable
private fun PdfMaxTheme(dark: Boolean, content: @Composable () -> Unit) {
    val scheme = if (dark) darkColorScheme(
        primary = Blue, secondary = Violet, tertiary = Cyan,
        background = Color(0xFF07142B), surface = Color(0xFF0D1C35),
        surfaceVariant = Color(0xFF112642), onPrimary = Color.White,
        onBackground = Color(0xFFF3F7FF), onSurface = Color(0xFFF3F7FF),
        onSurfaceVariant = Color(0xFFB8C8DD), outline = Color(0xFF254365)
    ) else lightColorScheme(
        primary = Color(0xFF176FE5), secondary = Color(0xFF7047DA),
        tertiary = Color(0xFF008FA6), background = Color(0xFFF3F6FC),
        surface = Color.White, surfaceVariant = Color(0xFFE9EFF8),
        onPrimary = Color.White, onBackground = Color(0xFF10213A),
        onSurface = Color(0xFF10213A), onSurfaceVariant = Color(0xFF52647D),
        outline = Color(0xFFD5DFEC)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

private enum class Page { HOME, FILES, TOOLS, SETTINGS, READER }
private enum class Group { EDIT, ORGANIZE, CONVERT, PROTECT, SCAN, EXTRA }
private data class Tool(val pt: String, val en: String, val descPt: String, val descEn: String, val icon: ImageVector, val tint: Color, val group: Group)
private data class DemoFile(val name: String, val meta: String, val color: Color)

private val tools = listOf(
    Tool("Editar PDF", "Edit PDF", "Texto, imagens e anotações", "Text, images and annotations", Icons.Filled.Edit, Violet, Group.EDIT),
    Tool("Converter PDF", "Convert PDF", "Converta para outros formatos", "Convert to other formats", Icons.Filled.Transform, Blue, Group.CONVERT),
    Tool("Comprimir PDF", "Compress PDF", "Reduza o tamanho do ficheiro", "Reduce file size", Icons.Filled.Compress, Green, Group.EXTRA),
    Tool("Juntar PDF", "Merge PDF", "Combine vários documentos", "Combine multiple documents", Icons.Filled.MergeType, Orange, Group.ORGANIZE),
    Tool("Dividir PDF", "Split PDF", "Separe páginas e intervalos", "Split pages and ranges", Icons.Filled.SplitScreen, Cyan, Group.ORGANIZE),
    Tool("Proteger PDF", "Protect PDF", "Adicione uma palavra-passe", "Add a password", Icons.Filled.Security, Pink, Group.PROTECT),
    Tool("Assinar PDF", "Sign PDF", "Assinatura e preenchimento", "Sign and fill documents", Icons.Filled.Draw, Violet, Group.EDIT),
    Tool("Digitalizar", "Scan", "Digitalize documentos", "Scan paper documents", Icons.Filled.CameraAlt, Blue, Group.SCAN),
    Tool("OCR", "OCR", "Reconheça texto em imagens", "Recognize text in images", Icons.Filled.TextFields, Green, Group.SCAN),
    Tool("Extrair imagens", "Extract images", "Guarde imagens do PDF", "Save images from a PDF", Icons.Filled.ImageIcon, Pink, Group.EXTRA),
    Tool("Rodar páginas", "Rotate pages", "Ajuste a orientação", "Adjust page orientation", Icons.Filled.RotateRight, Orange, Group.ORGANIZE),
    Tool("Numerar páginas", "Page numbers", "Adicione números às páginas", "Add page numbers", Icons.Filled.Numbers, Cyan, Group.ORGANIZE),
    Tool("Marca de água", "Watermark", "Texto ou imagem personalizada", "Custom text or image", Icons.Filled.Description, Violet, Group.EDIT),
    Tool("Comparar PDF", "Compare PDF", "Encontre diferenças", "Find document differences", Icons.Filled.CompareArrows, Blue, Group.EXTRA),
    Tool("Imprimir PDF", "Print PDF", "Prepare para impressão", "Prepare for printing", Icons.Filled.Print, Green, Group.EXTRA),
    Tool("Partilhar PDF", "Share PDF", "Partilhe os documentos", "Share your documents", Icons.Filled.Share, Orange, Group.EXTRA),
    Tool("Organizar páginas", "Organize pages", "Reordene e elimine páginas", "Reorder and remove pages", Icons.Filled.GridView, Cyan, Group.ORGANIZE),
    Tool("Preencher formulário", "Fill form", "Preencha campos", "Fill in form fields", Icons.Filled.CheckCircle, Pink, Group.EDIT)
)

@Composable
private fun PdfMaxApp(dark: Boolean, onThemeChange: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var page by rememberSaveable { mutableStateOf(Page.HOME) }
    var activeTool by remember { mutableStateOf<Tool?>(null) }
    var english by rememberSaveable { mutableStateOf(false) }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var fileName by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { result ->
        if (result != null) {
            try { context.contentResolver.takePersistableUriPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
            uri = result
            fileName = displayName(context, result)
            page = Page.READER
            activeTool = null
        }
    }
    val openPdf = { picker.launch(arrayOf("application/pdf")) }
    val navigate: (Page) -> Unit = {
        page = it
        activeTool = null
        scope.launch { drawer.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        scrimColor = Color.Black.copy(alpha = .68f),
        drawerContent = {
            PdfDrawer(
                english = english,
                onNavigate = navigate,
                onScanner = {
                    activeTool = tools.first { it.group == Group.SCAN }
                    scope.launch { drawer.close() }
                },
                onClose = { scope.launch { drawer.close() } }
            )
        }
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                when {
                    activeTool != null -> SimpleTopBar(if (english) activeTool!!.en else activeTool!!.pt, { activeTool = null })
                    page == Page.READER -> SimpleTopBar(fileName, { navigate(Page.HOME) }, {
                        IconButton(onClick = {}) { Icon(Icons.Filled.BookmarkBorder, null) }
                        IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, null) }
                    })
                    else -> HomeTopBar({ scope.launch { drawer.open() } }, { navigate(Page.SETTINGS) })
                }
            },
            bottomBar = {
                if (activeTool == null && page != Page.READER) {
                    BottomBar(page, english, { target -> if (target == null) openPdf() else navigate(target) }, { scope.launch { drawer.open() } })
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
                when {
                    activeTool != null -> ToolDetail(activeTool!!, english, openPdf)
                    page == Page.HOME -> HomeScreen(english, openPdf, { activeTool = it }, { navigate(Page.TOOLS) }, { navigate(Page.FILES) })
                    page == Page.FILES -> FilesScreen(english, openPdf)
                    page == Page.TOOLS -> ToolsScreen(english) { activeTool = it }
                    page == Page.SETTINGS -> SettingsScreen(english, { english = it }, dark, onThemeChange)
                    page == Page.READER && uri != null -> PdfReader(uri!!, fileName, english) { activeTool = tools.first { it.group == Group.EDIT } }
                }
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else "document.pdf"
    } ?: "document.pdf"
}

@Composable
private fun PdfMark(size: Int = 38) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF3A62), Violet, Blue))), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size((size * .62f).dp))
    }
}

@Composable
private fun HomeTopBar(onMenu: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 15.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onMenu) { Icon(Icons.Filled.Menu, "Menu", tint = MaterialTheme.colorScheme.onSurface) }
        PdfMark()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("PDF Max", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("PDF Editor & Reader", color = Muted, fontSize = 10.sp)
        }
        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun SimpleTopBar(title: String, onBack: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onSurface) }
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
    }
}

@Composable
private fun BottomBar(page: Page, en: Boolean, onNavigate: (Page?) -> Unit, onMenu: () -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
        NavigationBarItem(page == Page.HOME, { onNavigate(Page.HOME) }, { Icon(Icons.Filled.Home, null) }, label = { Text(tr(en, "Início", "Home"), fontSize = 10.sp) })
        NavigationBarItem(page == Page.FILES, { onNavigate(Page.FILES) }, { Icon(Icons.Filled.FolderOpen, null) }, label = { Text(tr(en, "Arquivos", "Files"), fontSize = 10.sp) })
        NavigationBarItem(false, { onNavigate(null) }, {
            Box(Modifier.size(42.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Blue, Violet))), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Add, null, tint = Color.White)
            }
        }, label = { Text(tr(en, "Abrir", "Open"), fontSize = 10.sp) })
        NavigationBarItem(page == Page.TOOLS, { onNavigate(Page.TOOLS) }, { Icon(Icons.Filled.Apps, null) }, label = { Text(tr(en, "Ferramentas", "Tools"), fontSize = 10.sp) })
        NavigationBarItem(false, onMenu, { Icon(Icons.Filled.Menu, null) }, label = { Text(tr(en, "Menu", "Menu"), fontSize = 10.sp) })
    }
}

@Composable
private fun HomeScreen(en: Boolean, openPdf: () -> Unit, onTool: (Tool) -> Unit, seeTools: () -> Unit, seeFiles: () -> Unit) {
    val quick = listOf(tools[0], tools[1], tools[2], tools[3], tools[5], tools[7])
    val samples = listOf(
        DemoFile("Relatório_2025.pdf", "2.4 MB  •  Hoje, 09:12", Pink),
        DemoFile("Apresentação.pdf", "5.8 MB  •  Ontem, 16:45", Violet),
        DemoFile("Contrato.pdf", "1.2 MB  •  28 Out, 11:20", Green)
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(17.dp)) {
        item {
            Column {
                Text(tr(en, "Olá! 👋", "Hello! 👋"), color = MaterialTheme.colorScheme.onSurface, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(tr(en, "Os teus documentos, num só lugar.", "All your documents in one place."), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), RoundedCornerShape(15.dp)).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Search, null, tint = Muted)
                Spacer(Modifier.width(10.dp))
                Text(tr(en, "Pesquisar documentos...", "Search documents..."), color = Muted, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                Icon(Icons.Filled.Tune, null, tint = Muted)
            }
        }
        item {
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Color.Transparent), modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF173B83), Color(0xFF4D35A6), Color(0xFF142950))), RoundedCornerShape(22.dp))) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, null, tint = Cyan, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("PDF MAX • WORKSPACE", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                        Text(tr(en, "Tudo o que precisas para os teus PDFs.", "Everything you need for your PDFs."), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp)
                        Text(tr(en, "Lê, edita, converte e protege documentos.", "Read, edit, convert and protect documents."), color = Color(0xFFD0DCFF), fontSize = 11.sp)
                        Button(onClick = seeTools, shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
                            Text(tr(en, "Explorar ferramentas", "Explore tools"), fontSize = 11.sp)
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Filled.ChevronRight, null, Modifier.size(15.dp))
                        }
                    }
                    Box(Modifier.size(70.dp).clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.PictureAsPdf, null, tint = Color.White, modifier = Modifier.size(46.dp))
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(tr(en, "Acesso rápido", "Quick access"), tr(en, "Ver tudo", "See all"), seeTools)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickAction(Icons.Filled.PictureAsPdf, tr(en, "Abrir PDF", "Open PDF"), Pink, openPdf, Modifier.weight(1f))
                    QuickAction(Icons.Filled.CameraAlt, tr(en, "Digitalizar", "Scan"), Blue, { onTool(tools[7]) }, Modifier.weight(1f))
                    QuickAction(Icons.Filled.Transform, tr(en, "Converter", "Convert"), Green, { onTool(tools[1]) }, Modifier.weight(1f))
                    QuickAction(Icons.Filled.MoreHoriz, tr(en, "Mais", "More"), Violet, seeTools, Modifier.weight(1f))
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle(tr(en, "Ferramentas populares", "Popular tools"), tr(en, "Todas", "All"), seeTools)
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.height(232.dp), userScrollEnabled = false, horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    items(quick) { item -> ToolTile(item, en) { onTool(item) } }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SectionTitle(tr(en, "Documentos recentes", "Recent documents"), tr(en, "Ver todos", "View all"), seeFiles)
                samples.forEach { RecentRow(it, openPdf) }
                Text(tr(en, "Os documentos acima são exemplos visuais; os teus ficheiros serão apresentados aqui.", "These are visual samples; your files will appear here."), color = Muted, fontSize = 9.sp)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String, onAction: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        TextButton(onClick = onAction, contentPadding = PaddingValues(4.dp)) {
            Text(action, color = Blue, fontSize = 11.sp)
            Icon(Icons.Filled.ChevronRight, null, tint = Blue, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, color: Color, action: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), RoundedCornerShape(14.dp)).clickable(onClick = action).padding(vertical = 11.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        }
        Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun ToolTile(tool: Tool, en: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f), RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 11.dp, horizontal = 5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(tool.tint.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Icon(tool.icon, null, tint = tool.tint, modifier = Modifier.size(20.dp))
        }
        Text(if (en) tool.en else tool.pt, color = MaterialTheme.colorScheme.onSurface, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RecentRow(file: DemoFile, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surface).clickable(onClick = onClick).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp)).background(file.color.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PictureAsPdf, null, tint = file.color)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(file.name, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text(file.meta, color = Muted, fontSize = 9.sp)
        }
        Icon(Icons.Filled.MoreVert, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ToolsScreen(en: Boolean, onTool: (Tool) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val filters = listOf("all" to tr(en, "Todas", "All"), "edit" to tr(en, "Editar", "Edit"), "organize" to tr(en, "Organizar", "Organize"), "convert" to tr(en, "Converter", "Convert"), "protect" to tr(en, "Segurança", "Security"), "scan" to "OCR")
    val visible = tools.filter { filter == "all" || when (filter) {
        "edit" -> it.group == Group.EDIT
        "organize" -> it.group == Group.ORGANIZE
        "convert" -> it.group == Group.CONVERT
        "protect" -> it.group == Group.PROTECT
        "scan" -> it.group == Group.SCAN
        else -> true
    } }
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Text(tr(en, "Todas as ferramentas", "All tools"), color = MaterialTheme.colorScheme.onSurface, fontSize = 23.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
        Text(tr(en, "Um conjunto completo para trabalhar com PDFs.", "A complete PDF productivity toolkit."), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            filters.forEach { (key, label) -> FilterChip(filter == key, { filter = key }, label = { Text(label, fontSize = 10.sp) }) }
        }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            items(visible) { tool ->
                Column(Modifier.fillMaxWidth().height(114.dp).clip(RoundedCornerShape(15.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), RoundedCornerShape(15.dp)).clickable { onTool(tool) }.padding(10.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Box(Modifier.size(37.dp).clip(RoundedCornerShape(11.dp)).background(tool.tint.copy(alpha = .15f)), contentAlignment = Alignment.Center) { Icon(tool.icon, null, tint = tool.tint) }
                    Text(if (en) tool.en else tool.pt, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun FilesScreen(en: Boolean, openPdf: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("all") }
    val folders = listOf("Documentos", "Trabalho", "Pessoal", "Imagens", "Downloads")
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(Modifier.padding(top = 10.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(en, "Meus arquivos", "My files"), color = MaterialTheme.colorScheme.onSurface, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text(tr(en, "Organiza os teus documentos.", "Keep your documents organized."), color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = openPdf) { Icon(Icons.Filled.Add, null, tint = Blue) }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("all" to tr(en, "Todos", "All"), "pdf" to "PDF", "docs" to tr(en, "Documentos", "Documents"), "images" to tr(en, "Imagens", "Images")).forEach { (key, label) ->
                FilterChip(filter == key, { filter = key }, label = { Text(label, fontSize = 10.sp) })
            }
        }
        Text(tr(en, "Pastas", "Folders"), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 17.dp, bottom = 9.dp))
        folders.chunked(2).forEachIndexed { rowIndex, row ->
            Row(Modifier.padding(bottom = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                row.forEachIndexed { index, folder ->
                    val tint = listOf(Blue, Orange, Violet, Cyan, Green)[rowIndex * 2 + index]
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), RoundedCornerShape(14.dp)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Folder, null, tint = tint, modifier = Modifier.size(23.dp))
                        Spacer(Modifier.width(7.dp))
                        Column {
                            Text(tr(en, folder, listOf("Documents", "Work", "Personal", "Images", "Downloads")[rowIndex * 2 + index]), color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            Text(listOf("12", "8", "15", "24", "6")[rowIndex * 2 + index] + " " + tr(en, "itens", "items"), color = Muted, fontSize = 9.sp)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(tr(en, "Acedidos recentemente", "Recently accessed"), color = MaterialTheme.colorScheme.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 9.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RecentRow(DemoFile("Relatório_2025.pdf", "2.4 MB", Pink), openPdf)
            RecentRow(DemoFile("Apresentação.pdf", "5.8 MB", Violet), openPdf)
            RecentRow(DemoFile("Contrato.pdf", "1.2 MB", Green), openPdf)
        }
        Spacer(Modifier.height(8.dp))
        Text(tr(en, "Itens de demonstração. Usa + para abrir os teus PDFs.", "Demo items. Use + to open your own PDFs."), color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun ToolDetail(tool: Tool, en: Boolean, openPdf: () -> Unit) {
    var output by remember(tool.pt) { mutableStateOf("Word") }
    var level by remember(tool.pt) { mutableIntStateOf(1) }
    var password by remember(tool.pt) { mutableStateOf("") }
    var confirm by remember(tool.pt) { mutableStateOf("") }
    val outputs = listOf("Word", "Excel", "PowerPoint", "Imagem", "PDF")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (en) tool.en else tool.pt, color = MaterialTheme.colorScheme.onSurface, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(if (en) tool.descEn else tool.descPt, color = Muted, fontSize = 12.sp)
        }
        SectionCard(tr(en, "Ficheiro de origem", "Source file")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.PictureAsPdf, null, tint = Pink, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(en, "Selecionar documento", "Choose a document"), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("PDF • " + tr(en, "Do teu dispositivo", "From your device"), color = Muted, fontSize = 10.sp)
                }
                TextButton(onClick = openPdf) { Text(tr(en, "Escolher", "Browse"), color = Blue, fontSize = 11.sp) }
            }
        }
        when (tool.group) {
            Group.CONVERT -> SectionCard(tr(en, "Converter para", "Convert to")) {
                outputs.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { format ->
                            val chosen = output == format
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (chosen) Blue.copy(alpha = .15f) else MaterialTheme.colorScheme.surfaceVariant).border(1.dp, if (chosen) Blue else MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp)).clickable { output = format }.padding(11.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.Description, null, tint = if (chosen) Blue else Muted)
                                Text(format, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Group.EXTRA -> SectionCard(tr(en, "Nível de compressão", "Compression level")) {
                Text(listOf(tr(en, "Qualidade máxima", "Best quality"), tr(en, "Equilibrado", "Balanced"), tr(en, "Ficheiro menor", "Smallest file"))[level], color = Blue, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Slider(value = level.toFloat(), onValueChange = { level = it.toInt().coerceIn(0, 2) }, valueRange = 0f..2f, steps = 1)
            }
            Group.PROTECT -> SectionCard(tr(en, "Segurança do documento", "Document security")) {
                OutlinedTextField(password, { password = it }, label = { Text(tr(en, "Palavra-passe", "Password")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(confirm, { confirm = it }, label = { Text(tr(en, "Confirmar palavra-passe", "Confirm password")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
            Group.ORGANIZE -> SectionCard(tr(en, "Opções de páginas", "Page options")) {
                OptionLine(Icons.Filled.GridView, tr(en, "Selecionar páginas", "Select pages"), "1–24")
                OptionLine(Icons.Filled.RotateRight, tr(en, "Orientação", "Orientation"), tr(en, "Automática", "Automatic"))
                OptionLine(Icons.Filled.Delete, tr(en, "Páginas a remover", "Pages to remove"), "—")
            }
            Group.SCAN -> SectionCard(tr(en, "Modo de digitalização", "Scan mode")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(tr(en, "Documento", "Document"), tr(en, "Foto", "Photo"), "OCR").forEachIndexed { index, label ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(if (index == 0) Icons.Filled.Description else if (index == 1) ImageIcon else Icons.Filled.TextFields, null, tint = listOf(Blue, Violet, Green)[index])
                            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 10.sp)
                        }
                    }
                }
            }
            Group.EDIT -> SectionCard(tr(en, "Ferramentas de edição", "Editing tools")) {
                OptionLine(Icons.Filled.TextFields, tr(en, "Adicionar texto", "Add text"), tr(en, "Pronto", "Ready"))
                OptionLine(Icons.Filled.ImageIcon, tr(en, "Inserir imagem", "Insert image"), tr(en, "Escolher", "Choose"))
                OptionLine(Icons.Filled.Draw, tr(en, "Desenho e assinatura", "Draw and signature"), tr(en, "Abrir", "Open"))
            }
        }
        SectionCard(tr(en, "Pré-visualização", "Preview")) {
            Box(Modifier.fillMaxWidth().height(135.dp).clip(RoundedCornerShape(12.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.PictureAsPdf, null, tint = Pink, modifier = Modifier.size(32.dp))
                    Text(tr(en, "A pré-visualização aparecerá aqui", "Your preview will appear here"), color = Color(0xFF53657D), fontSize = 10.sp)
                }
            }
        }
        Button(onClick = openPdf, Modifier.fillMaxWidth().height(51.dp), shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Blue)) {
            Icon(Icons.Filled.PictureAsPdf, null)
            Spacer(Modifier.width(8.dp))
            Text(tr(en, "Selecionar PDF", "Select PDF"), fontWeight = FontWeight.Bold)
        }
        Text(tr(en, "O ecrã e as opções visuais estão preparados. O motor de processamento desta ferramenta será integrado por etapas.", "The screen and controls are prepared. The processing engine for this tool will be integrated in stages."), color = Muted, fontSize = 9.sp)
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .5f), RoundedCornerShape(16.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
        Text(title, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
private fun OptionLine(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Blue, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
        Text(value, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun PdfReader(uri: Uri, fileName: String, en: Boolean, openEdit: () -> Unit) {
    var pageIndex by remember(uri) { mutableIntStateOf(0) }
    var pageCount by remember(uri) { mutableIntStateOf(0) }
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(uri) { mutableStateOf<String?>(null) }
    var loading by remember(uri) { mutableStateOf(true) }
    val context = LocalContext.current
    LaunchedEffect(uri, pageIndex) {
        loading = true
        error = null
        try {
            val rendered = withContext(Dispatchers.IO) { renderPdfPage(context, uri, pageIndex) }
            pageCount = rendered.first
            bitmap = rendered.second.asImageBitmap()
        } catch (e: Exception) {
            error = e.localizedMessage ?: tr(en, "Não foi possível abrir o PDF.", "Could not open this PDF.")
        } finally {
            loading = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(fileName, color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (pageCount > 0) (pageIndex + 1).toString() + " / " + pageCount else tr(en, "A abrir documento...", "Opening document..."), color = Muted, fontSize = 9.sp)
            }
            IconButton(onClick = openEdit) { Icon(Icons.Filled.Edit, null, tint = Blue) }
            IconButton(onClick = {}) { Icon(Icons.Filled.Search, null, tint = Muted) }
            IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, null, tint = Muted) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ReaderAction(Icons.Filled.BookmarkBorder, tr(en, "Marcador", "Bookmark"), Modifier.weight(1f))
            ReaderAction(Icons.Filled.Search, tr(en, "Pesquisar", "Search"), Modifier.weight(1f))
            ReaderAction(Icons.Filled.DarkMode, tr(en, "Noturno", "Night"), Modifier.weight(1f))
            ReaderAction(Icons.Filled.Share, tr(en, "Partilhar", "Share"), Modifier.weight(1f))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(15.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFF192A42)), contentAlignment = Alignment.Center) {
            when {
                loading -> Text(tr(en, "A carregar página...", "Loading page..."), color = Color.White, fontSize = 11.sp)
                error != null -> Text(error ?: "", color = Color(0xFFFF9D9D), fontSize = 11.sp, modifier = Modifier.padding(18.dp))
                bitmap != null -> Image(bitmap!!, fileName, Modifier.fillMaxWidth().padding(8.dp), contentScale = ContentScale.Fit)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { pageIndex = (pageIndex - 1).coerceAtLeast(0) }, enabled = pageIndex > 0) { Text(tr(en, "Anterior", "Previous")) }
            Text((pageIndex + 1).toString() + " / " + pageCount.coerceAtLeast(1), color = MaterialTheme.colorScheme.onSurface, fontSize = 11.sp)
            TextButton(onClick = { pageIndex = (pageIndex + 1).coerceAtMost((pageCount - 1).coerceAtLeast(0)) }, enabled = pageCount > 0 && pageIndex < pageCount - 1) { Text(tr(en, "Seguinte", "Next")) }
        }
    }
}

private fun renderPdfPage(context: Context, uri: Uri, requestedPage: Int): Pair<Int, Bitmap> {
    val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Não foi possível aceder ao documento.")
    descriptor.use { fd ->
        PdfRenderer(fd).use { renderer ->
            require(renderer.pageCount > 0) { "O PDF não contém páginas." }
            val index = requestedPage.coerceIn(0, renderer.pageCount - 1)
            renderer.openPage(index).use { page ->
                val scale = minOf(2f, 1800f / page.width.coerceAtLeast(1))
                val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return renderer.pageCount to bitmap
            }
        }
    }
}

@Composable
private fun ReaderAction(icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, tint = Blue, modifier = Modifier.size(17.dp))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 8.sp, maxLines = 1)
    }
}

@Composable
private fun SettingsScreen(en: Boolean, language: (Boolean) -> Unit, dark: Boolean, theme: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr(en, "Configurações", "Settings"), color = MaterialTheme.colorScheme.onSurface, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text(tr(en, "Personaliza a tua experiência no PDF Max.", "Personalize your PDF Max experience."), color = Muted, fontSize = 12.sp)
        SectionCard(tr(en, "Aparência", "Appearance")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (dark) Icons.Filled.DarkMode else Icons.Filled.WbSunny, null, tint = Violet)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(en, "Modo escuro", "Dark mode"), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(tr(en, "Interface azul-marinho", "Deep navy interface"), color = Muted, fontSize = 9.sp)
                }
                Switch(dark, theme)
            }
        }
        SectionCard(tr(en, "Idioma", "Language")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Language, null, tint = Blue)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(en, "Idioma da aplicação", "App language"), color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (en) "English" else "Português", color = Muted, fontSize = 9.sp)
                }
                TextButton(onClick = { language(!en) }) { Text(if (en) "PT" else "EN", color = Blue, fontWeight = FontWeight.Bold) }
            }
        }
        SectionCard(tr(en, "Privacidade e segurança", "Privacy & security")) {
            OptionLine(Icons.Filled.Lock, tr(en, "Proteção de documentos", "Document protection"), tr(en, "Configurar", "Configure"))
            OptionLine(Icons.Filled.Cloud, tr(en, "Sincronização", "Synchronization"), tr(en, "Desativada", "Off"))
            OptionLine(Icons.Filled.Delete, tr(en, "Limpar cache", "Clear cache"), "")
        }
        SectionCard(tr(en, "Sobre", "About")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PdfMark(42)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("PDF Max", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("PDF Editor & Reader  •  v0.1.0", color = Muted, fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable
private fun PdfDrawer(en: Boolean, onNavigate: (Page) -> Unit, onScanner: () -> Unit, onClose: () -> Unit) {
    ModalDrawerSheet(drawerContainerColor = MaterialTheme.colorScheme.surface, drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp), modifier = Modifier.width(310.dp)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PdfMark(48)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("PDF Max", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("PDF Editor & Reader", color = Muted, fontSize = 9.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.MoreHoriz, null, tint = Muted) }
            }
            Spacer(Modifier.height(22.dp))
            DrawerLabel(tr(en, "ESPAÇO DE TRABALHO", "WORKSPACE"))
            DrawerRow(Icons.Filled.Home, tr(en, "Início", "Home"), true) { onNavigate(Page.HOME) }
            DrawerRow(Icons.Filled.FolderOpen, tr(en, "Meus arquivos", "My files")) { onNavigate(Page.FILES) }
            DrawerRow(Icons.Filled.History, tr(en, "Recentes", "Recent")) { onNavigate(Page.FILES) }
            DrawerRow(Icons.Filled.Star, tr(en, "Favoritos", "Favorites")) { onNavigate(Page.FILES) }
            DrawerRow(Icons.Filled.Cloud, tr(en, "Nuvem", "Cloud")) { onNavigate(Page.FILES) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f), modifier = Modifier.padding(vertical = 12.dp))
            DrawerLabel(tr(en, "FERRAMENTAS", "TOOLS"))
            DrawerRow(Icons.Filled.Apps, tr(en, "Todas as ferramentas", "All tools")) { onNavigate(Page.TOOLS) }
            DrawerRow(Icons.Filled.CameraAlt, tr(en, "Scanner e OCR", "Scanner & OCR"), onClick = onScanner)
            DrawerRow(Icons.Filled.PictureAsPdf, tr(en, "Leitor PDF", "PDF Reader")) { onNavigate(Page.HOME) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f), modifier = Modifier.padding(vertical = 12.dp))
            DrawerLabel(tr(en, "PREFERÊNCIAS", "PREFERENCES"))
            DrawerRow(Icons.Filled.Settings, tr(en, "Configurações", "Settings")) { onNavigate(Page.SETTINGS) }
            DrawerRow(Icons.Filled.HelpOutline, tr(en, "Ajuda e suporte", "Help & support")) { onNavigate(Page.SETTINGS) }
            DrawerRow(Icons.Filled.Info, tr(en, "Sobre o PDF Max", "About PDF Max")) { onNavigate(Page.SETTINGS) }
            Spacer(Modifier.weight(1f))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, null, tint = Violet)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("PDF Max Premium", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(tr(en, "Mais produtividade, sem limites.", "More productivity, fewer limits."), color = Muted, fontSize = 9.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("PDF Max  •  0.1.0", color = Muted, fontSize = 9.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@Composable
private fun DrawerLabel(text: String) {
    Text(text, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
}

@Composable
private fun DrawerRow(icon: ImageVector, title: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(if (selected) Blue.copy(alpha = .17f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (selected) Blue else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(12.dp))
        Text(title, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
    }
}
