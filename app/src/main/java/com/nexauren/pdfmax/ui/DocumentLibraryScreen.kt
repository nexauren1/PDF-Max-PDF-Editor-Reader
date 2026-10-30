package com.nexauren.pdfmax.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexauren.pdfmax.data.DocumentKind
import com.nexauren.pdfmax.data.DocumentScanner
import com.nexauren.pdfmax.data.IndexedDocument
import com.nexauren.pdfmax.data.OfficeTextExtractor
import com.nexauren.pdfmax.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.graphics.BitmapFactory

@Composable
fun DocumentLibraryScreen(english: Boolean, onOpenFile: (File) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(DocumentScanner.hasAccess(context)) }
    var files by remember { mutableStateOf<List<IndexedDocument>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("All") }
    var scanned by remember { mutableStateOf(false) }

    fun refresh() {
        access = DocumentScanner.hasAccess(context)
        if (!access) return
        loading = true
        scope.launch {
            files = withContext(Dispatchers.IO) { DocumentScanner.scan(context) }
            loading = false
            scanned = true
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:" + context.packageName)
            }
            runCatching { context.startActivity(intent) }.onFailure {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val visible = remember(files, query, filter) {
        files.filter { item ->
            val matchesQuery = query.isBlank() || item.name.contains(query, true) || item.file.parent.orEmpty().contains(query, true)
            val matchesType = when (filter) {
                "PDF" -> item.kind == DocumentKind.PDF
                "Word" -> item.kind == DocumentKind.WORD
                "Excel" -> item.kind == DocumentKind.EXCEL
                "PPT" -> item.kind == DocumentKind.POWERPOINT
                "Images" -> item.kind == DocumentKind.IMAGE
                else -> true
            }
            matchesQuery && matchesType
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (english) "My files" else "Meus Arquivos", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (access) {
                        if (english) files.size.toString() + " files found on this device" else files.size.toString() + " ficheiros encontrados neste dispositivo"
                    } else {
                        if (english) "Internal document library" else "Biblioteca interna de documentos"
                    },
                    color = MaxMuted, fontSize = 11.sp
                )
            }
            IconButton(onClick = { refresh() }, enabled = access && !loading) { Icon(Icons.Default.Refresh, null, tint = MaxCyan) }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = MaxCyan, strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF172A49)).padding(horizontal = 13.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = MaxMuted, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            BasicTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 13.sp),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (query.isEmpty()) Text(if (english) "Search name, folder or format…" else "Pesquisar nome, pasta ou formato…", color = MaxMuted, fontSize = 12.sp)
                    inner()
                }
            )
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.Close, null, tint = MaxMuted, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(11.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("All", "PDF", "Word", "Excel", "PPT", "Images").forEach { label ->
                val selected = filter == label
                Box(Modifier.clip(CircleShape).background(if (selected) Color(0xFF087EEB) else Color(0xFF172A47)).clickable { filter = label }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(when (label) {
                        "All" -> if (english) "All" else "Todos"
                        "Images" -> if (english) "Images" else "Imagens"
                        else -> label
                    }, color = Color.White, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        if (!access) {
            AccessCard(english, onRequest = { requestAccess() })
        } else if (loading && files.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaxCyan)
                    Spacer(Modifier.height(12.dp))
                    Text(if (english) "Scanning documents…" else "A analisar documentos…", color = MaxMuted, fontSize = 12.sp)
                }
            }
        } else if (visible.isEmpty()) {
            EmptyFilesCard(english, query.isNotBlank() || scanned)
        } else {
            Text(visible.size.toString() + if (english) " RESULTS" else " RESULTADOS", color = MaxMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 7.dp))
            LazyColumn(contentPadding = PaddingValues(bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(visible, key = { it.file.absolutePath }) { item -> DocumentRow(item) { onOpenFile(item.file) } }
            }
        }
    }
}

@Composable
private fun AccessCard(english: Boolean, onRequest: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(19.dp)).background(Color(0xFF102541)).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(74.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF168BFF).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FolderSpecial, null, tint = MaxCyan, modifier = Modifier.size(39.dp))
        }
        Spacer(Modifier.height(13.dp))
        Text(if (english) "Enable the PDF Max file library" else "Ative a biblioteca de ficheiros do PDF Max", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text(if (english) "PDF Max can scan shared storage and find PDF, Word, Excel, PowerPoint, images and other documents. Files stay on your device." else "O PDF Max pode analisar o armazenamento partilhado e encontrar PDFs, Word, Excel, PowerPoint, imagens e outros documentos. Os ficheiros permanecem no seu dispositivo.", color = MaxMuted, fontSize = 11.sp, lineHeight = 16.sp)
        Spacer(Modifier.height(14.dp))
        Button(onClick = onRequest, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FF1)), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.FolderOpen, null)
            Spacer(Modifier.width(8.dp))
            Text(if (english) "Grant file access" else "Permitir acesso aos ficheiros")
        }
        Text(if (english) "Android will ask you to allow access in system settings." else "O Android pedirá autorização nas definições do sistema.", color = Color(0xFF8295B4), fontSize = 9.sp)
    }
}

@Composable
private fun EmptyFilesCard(english: Boolean, searched: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 35.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.FolderOff, null, tint = Color(0xFF58708F), modifier = Modifier.size(43.dp))
        Spacer(Modifier.height(10.dp))
        Text(if (searched) { if (english) "No matching documents" else "Nenhum documento encontrado" } else { if (english) "No documents found yet" else "Ainda não foram encontrados documentos" }, color = Color.White, fontWeight = FontWeight.SemiBold)
        Text(if (english) "Try another search or scan again." else "Experimente outra pesquisa ou volte a analisar.", color = MaxMuted, fontSize = 11.sp)
    }
}

@Composable
private fun DocumentRow(item: IndexedDocument, onClick: () -> Unit) {
    val (icon, accent) = when (item.kind) {
        DocumentKind.PDF -> Icons.Default.PictureAsPdf to Color(0xFFFF4D7A)
        DocumentKind.WORD -> Icons.Default.Description to Color(0xFF398BFF)
        DocumentKind.EXCEL -> Icons.Default.TableChart to Color(0xFF18B989)
        DocumentKind.POWERPOINT -> Icons.Default.Slideshow to Color(0xFFFF8A45)
        DocumentKind.IMAGE -> Icons.Default.Image to Color(0xFFA66BFF)
        DocumentKind.TEXT -> Icons.Default.Article to Color(0xFF28B8D2)
        DocumentKind.ARCHIVE -> Icons.Default.FolderZip to Color(0xFFE2A33D)
        DocumentKind.OTHER -> Icons.Default.InsertDriveFile to Color(0xFF94A7C4)
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.17f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent, modifier = Modifier.size(23.dp)) }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.file.parent.orEmpty(), color = MaxMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.sizeLabel + "  •  " + item.extension.uppercase(), color = Color(0xFF7890B2), fontSize = 9.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = MaxMuted, modifier = Modifier.size(18.dp))
    }
    Divider(color = Color(0xFF1B2D49))
}

@Composable
fun OfficePreviewScreen(file: File?, english: Boolean, onBack: () -> Unit) {
    var text by remember(file) { mutableStateOf<String?>(null) }
    var bitmap by remember(file) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(file) {
        text = null
        bitmap = null
        if (file != null) {
            if (file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")) {
                bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(file.absolutePath) }
            } else {
                text = withContext(Dispatchers.IO) { OfficeTextExtractor.extract(file) }
            }
        }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(file?.name ?: "Document", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (english) "Document preview" else "Pré-visualização do documento", color = MaxMuted, fontSize = 10.sp)
            }
            Icon(Icons.Default.MoreVert, null, tint = MaxMuted)
        }
        Spacer(Modifier.height(8.dp))
        when {
            bitmap != null -> androidx.compose.foundation.Image(bitmap!!.asImageBitmap(), null, modifier = Modifier.fillMaxWidth().weight(1f), contentScale = androidx.compose.ui.layout.ContentScale.Fit)
            text != null -> Column(Modifier.fillMaxSize().clip(RoundedCornerShape(13.dp)).background(Color(0xFFF8FAFF)).padding(16.dp).verticalScroll(rememberScrollState())) {
                Text(text ?: "", color = Color(0xFF17243A), fontSize = 13.sp, lineHeight = 19.sp)
            }
            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MaxCyan) }
        }
    }
}
