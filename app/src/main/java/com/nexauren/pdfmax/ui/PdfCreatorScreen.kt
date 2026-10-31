package com.nexauren.pdfmax.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexauren.pdfmax.data.DocumentScanner
import com.nexauren.pdfmax.data.PdfComposerOperations
import com.nexauren.pdfmax.ui.theme.MaxCyan
import com.nexauren.pdfmax.ui.theme.MaxMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun PdfCreatorScreen(english: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    var title by remember { mutableStateOf("") }
    var subtitle by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var family by remember { mutableStateOf("Sans") }
    var style by remember { mutableStateOf("Normal") }
    var size by remember { mutableFloatStateOf(11f) }
    var tableOn by remember { mutableStateOf(true) }
    var rows by remember {
        mutableStateOf(listOf(listOf(if (english) "Column 1" else "Coluna 1", if (english) "Column 2" else "Coluna 2", if (english) "Column 3" else "Coluna 3"), listOf("", "", "")))
    }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var output by remember { mutableStateOf<File?>(null) }
    var access by remember { mutableStateOf(DocumentScanner.hasAccess(context)) }

    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) access = DocumentScanner.hasAccess(context) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun storageSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
        runCatching { context.startActivity(intent) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName)))
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(Brush.linearGradient(listOf(Color(0xFFE65A82), Color(0xFF8C48E8), Color(0xFF287FEA)))), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.EditNote, null, tint = Color.White, modifier = Modifier.size(29.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (english) "PDF Writing Lab" else "Laboratório de escrita PDF", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text(if (english) "Design documents with structure and style." else "Crie documentos com estrutura e apresentação profissional.", color = MaxMuted, fontSize = 10.sp)
            }
            Icon(Icons.Default.AutoAwesome, null, tint = Color(0xFFE65A82))
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Brush.linearGradient(listOf(Color(0xFF172F54), Color(0xFF10223C)))).border(1.dp, Color(0xFF36577E), RoundedCornerShape(17.dp)).padding(14.dp)) {
            Text(if (english) "LIVE DOCUMENT PREVIEW" else "PRÉ-VISUALIZAÇÃO", color = Color(0xFF79C9FF), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF8FAFE)).padding(17.dp)) {
                Text(title.ifBlank { if (english) "Your document title" else "Título do seu documento" }, color = Color(0xFF17243A), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (subtitle.isNotBlank()) Text(subtitle, color = Color(0xFF526782), fontSize = 11.sp)
                Divider(Modifier.padding(vertical = 9.dp), color = Color(0xFFB5C6DB))
                Text(body.ifBlank { if (english) "Your text will appear here…" else "O texto aparecerá aqui enquanto escreve…" }, color = Color(0xFF26364C), fontSize = 10.sp, lineHeight = 15.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
                if (tableOn) {
                    Spacer(Modifier.height(8.dp))
                    rows.take(3).forEach { Text(it.joinToString("  |  "), color = Color(0xFF34445B), fontSize = 8.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }

        Panel(if (english) "01  DOCUMENT DETAILS" else "01  IDENTIFICAÇÃO") {
            Field(title, { title = it }, if (english) "Title" else "Título", if (english) "Document title" else "Título do documento")
            Field(subtitle, { subtitle = it }, if (english) "Subtitle" else "Subtítulo", if (english) "Optional subtitle" else "Subtítulo opcional")
        }
        Panel(if (english) "02  WRITING & TYPOGRAPHY" else "02  TEXTO E TIPOGRAFIA") {
            Field(body, { body = it }, if (english) "Main text" else "Texto principal", if (english) "Write paragraphs here…" else "Escreva os seus parágrafos…", 7)
            Spacer(Modifier.height(8.dp))
            Text(if (english) "Font family" else "Tipo de letra", color = MaxMuted, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Sans", "Serif", "Mono").forEach { f -> Chip(if (!english && f == "Sans") "Sem serifa" else f, family == f) { family = f } }
            }
            Spacer(Modifier.height(8.dp))
            Text(if (english) "Paragraph style" else "Estilo do texto", color = MaxMuted, fontSize = 10.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Normal", "Bold", "Italic").forEach { s -> Chip(if (english) s else when (s) { "Bold" -> "Negrito"; "Italic" -> "Itálico"; else -> s }, style == s) { style = s } }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (english) "Font size" else "Tamanho da letra", color = MaxMuted, fontSize = 10.sp, modifier = Modifier.weight(1f))
                Text(size.toInt().toString() + " pt", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Slider(value = size, onValueChange = { size = it }, valueRange = 9f..16f, steps = 6)
        }
        Panel(if (english) "03  TABLE BUILDER" else "03  CONSTRUTOR DE TABELAS") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (english) "Include a table" else "Incluir tabela", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (english) "Three columns with expandable rows" else "Três colunas e linhas adicionais", color = MaxMuted, fontSize = 9.sp)
                }
                Switch(checked = tableOn, onCheckedChange = { tableOn = it })
            }
            if (tableOn) {
                rows.forEachIndexed { index, row ->
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(Color(0xFF102541)).padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (index == 0) { if (english) "Column headings" else "Cabeçalhos" } else { (if (english) "Row " else "Linha ") + index }, color = Color(0xFF7CCBFF), fontSize = 9.sp, modifier = Modifier.weight(1f))
                            if (index > 0) IconButton(onClick = { rows = rows.filterIndexed { i, _ -> i != index } }, modifier = Modifier.size(26.dp)) { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF8198), modifier = Modifier.size(16.dp)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            row.forEachIndexed { col, cell ->
                                OutlinedTextField(
                                    value = cell,
                                    onValueChange = { v -> rows = rows.mapIndexed { i, cells -> if (i == index) cells.mapIndexed { j, old -> if (j == col) v else old } else cells } },
                                    modifier = Modifier.weight(1f), singleLine = true,
                                    textStyle = TextStyle(fontSize = 10.sp),
                                    label = { Text((if (english) "Cell " else "Célula ") + (col + 1), fontSize = 8.sp) },
                                    colors = fieldColors()
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = { rows = rows + listOf(listOf("", "", "")) }) {
                    Icon(Icons.Default.Add, null, tint = MaxCyan); Spacer(Modifier.width(5.dp))
                    Text(if (english) "Add row" else "Adicionar linha", color = MaxCyan)
                }
            }
        }

        if (!access) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF422C23)).padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FolderOpen, null, tint = Color(0xFFFFC07A))
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(if (english) "Storage access is required" else "É necessário permitir acesso", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(if (english) "Allow saving to Documents/PDF Max." else "Permita guardar em Documentos/PDF Max.", color = Color(0xFFE7C7A9), fontSize = 9.sp)
                }
                TextButton(onClick = { storageSettings() }) { Text(if (english) "Allow" else "Permitir", color = Color(0xFFFFC07A)) }
            }
        }
        Button(
            onClick = {
                message = null; output = null
                if (!DocumentScanner.hasAccess(context)) {
                    access = false
                    message = if (english) "Enable file access in Android settings, then return here." else "Ative o acesso aos ficheiros nas definições e volte aqui."
                    storageSettings()
                    return@Button
                }
                scope.launch {
                    busy = true
                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            PdfComposerOperations.createDocument(context, title, subtitle, body, family, style, size.toInt(), if (tableOn) rows else emptyList())
                        }
                    }
                    result.onSuccess {
                        output = it
                        withContext(Dispatchers.IO) { DocumentScanner.scan(context, forceRefresh = true) }
                    }.onFailure { message = it.message ?: if (english) "PDF creation failed." else "Falha ao criar o PDF." }
                    busy = false
                }
            },
            enabled = !busy && (title.isNotBlank() || body.isNotBlank() || (tableOn && rows.any { row -> row.any { it.isNotBlank() } })),
            modifier = Modifier.fillMaxWidth().height(53.dp), shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65A82))
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp) else Icon(Icons.Default.PictureAsPdf, null)
            Spacer(Modifier.width(9.dp))
            Text(if (busy) { if (english) "Creating PDF…" else "A criar PDF…" } else { if (english) "CREATE MY PDF" else "CRIAR O MEU PDF" }, color = Color.White, fontWeight = FontWeight.Bold)
        }
        message?.let { Text(it, color = Color(0xFFFF9AAE), fontSize = 10.sp) }
        output?.let { file ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF103A39)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF53E3B3))
                Column(Modifier.weight(1f).padding(start = 9.dp)) {
                    Text(if (english) "PDF created successfully" else "PDF criado com sucesso", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Text(file.name, color = Color(0xFFB8D8D3), fontSize = 9.sp)
                    Text(file.absolutePath, color = MaxMuted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable private fun Panel(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF0E203B)).border(1.dp, Color(0xFF233D5F), RoundedCornerShape(16.dp)).padding(13.dp)) {
        Text(title, color = Color(0xFF70C5FF), fontSize = 9.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(9.dp)); content()
    }
}
@Composable private fun Field(value: String, change: (String) -> Unit, label: String, hint: String, minLines: Int = 1) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, placeholder = { Text(hint, fontSize = 11.sp) }, minLines = minLines, singleLine = minLines == 1, colors = fieldColors())
}
@Composable private fun Chip(label: String, selected: Boolean, click: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) Color(0xFF1E63A8) else Color(0xFF172D49))
        .border(1.dp, if (selected) Color(0xFF5CB5FF) else Color(0xFF2A4668), RoundedCornerShape(10.dp)).clickable(onClick = click).padding(horizontal = 13.dp, vertical = 9.dp)) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}
@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = Color(0xFF4AAEFF),
    unfocusedBorderColor = Color(0xFF304766), focusedLabelColor = MaxCyan, unfocusedLabelColor = MaxMuted,
    cursorColor = MaxCyan, focusedContainerColor = Color(0xFF0D1D35), unfocusedContainerColor = Color(0xFF0D1D35)
)
