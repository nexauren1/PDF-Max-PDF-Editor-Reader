package com.nexauren.pdfmax.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexauren.pdfmax.data.PdfDocumentItem
import java.io.File

private data class WorkspaceCopy(
    val titlePt: String,
    val titleEn: String,
    val descPt: String,
    val descEn: String,
    val icon: ImageVector,
    val actionPt: String,
    val actionEn: String
)

private fun workspaceCopy(id: String): WorkspaceCopy = when (id) {
    "extract_pages" -> WorkspaceCopy("Extrair páginas", "Extract pages", "Escolhe exatamente as páginas que queres manter num novo PDF.", "Choose exactly which pages to keep in a new PDF.", Icons.Default.ContentCut, "Criar PDF com estas páginas", "Create PDF from these pages")
    "remove_pages" -> WorkspaceCopy("Remover páginas", "Remove pages", "Indica as páginas a retirar. O PDF final mantém a ordem das restantes.", "Enter the pages to remove. The output keeps the remaining order.", Icons.Default.DeleteSweep, "Remover páginas", "Remove pages")
    "rotate" -> WorkspaceCopy("Rodar páginas", "Rotate pages", "Aplica a mesma rotação a todas as páginas do documento.", "Apply the same rotation to every page in the document.", Icons.Default.RotateRight, "Aplicar rotação", "Apply rotation")
    "watermark" -> WorkspaceCopy("Marca de água", "Watermark", "Cria uma identificação discreta em todas as páginas.", "Place a discreet identity mark on every page.", Icons.Default.BrandingWatermark, "Adicionar marca de água", "Add watermark")
    "add_text" -> WorkspaceCopy("Adicionar texto", "Add text", "Adiciona uma nota visível por cima do conteúdo existente.", "Add a visible note over the existing page content.", Icons.Default.TextFields, "Adicionar texto ao PDF", "Add text to PDF")
    "page_numbers" -> WorkspaceCopy("Numerar páginas", "Page numbers", "Insere números no rodapé de todas as páginas.", "Insert page numbers in the footer of every page.", Icons.Default.FormatListNumbered, "Numerar documento", "Number document")
    "protect" -> WorkspaceCopy("Proteger PDF", "Protect PDF", "Define uma palavra-passe para abrir o documento.", "Set a password required to open the document.", Icons.Default.Lock, "Proteger documento", "Protect document")
    "unlock" -> WorkspaceCopy("Desproteger PDF", "Unlock PDF", "Remove a proteção quando tens a palavra-passe atual.", "Remove protection when you know the current password.", Icons.Default.LockOpen, "Desproteger documento", "Unlock document")
    "extract_text" -> WorkspaceCopy("Extrair texto", "Extract text", "Exporta para TXT o texto que já pode ser selecionado no PDF.", "Export selectable PDF text into a TXT file.", Icons.Default.TextSnippet, "Extrair texto", "Extract text")
    "to_images" -> WorkspaceCopy("PDF para imagens", "PDF to images", "Converte as páginas em imagens PNG reunidas num ZIP.", "Convert pages into PNG images inside a ZIP file.", Icons.Default.Image, "Exportar páginas", "Export pages")
    else -> WorkspaceCopy("Propriedades PDF", "PDF properties", "Atualiza o título e o autor guardados nos metadados do documento.", "Update the title and author stored in document metadata.", Icons.Default.EditNote, "Guardar propriedades", "Save properties")
}

@Composable
fun ToolWorkspaceScreen(
    toolId: String,
    english: Boolean,
    files: List<PdfDocumentItem>,
    onBack: () -> Unit,
    onImport: () -> Unit,
    onExecute: (File, Map<String, String>) -> Unit
) {
    val copy = remember(toolId) { workspaceCopy(toolId) }
    var selectedPath by rememberSaveable(toolId) { mutableStateOf<String?>(null) }
    var range by rememberSaveable(toolId) { mutableStateOf("1-3") }
    var degrees by rememberSaveable(toolId) { mutableIntStateOf(90) }
    var text by rememberSaveable(toolId) { mutableStateOf("") }
    var password by rememberSaveable(toolId) { mutableStateOf("") }
    var confirmation by rememberSaveable(toolId) { mutableStateOf("") }
    var showPassword by rememberSaveable(toolId) { mutableStateOf(false) }
    var dpi by rememberSaveable(toolId) { mutableFloatStateOf(144f) }
    var startNumber by rememberSaveable(toolId) { mutableStateOf("1") }
    var title by rememberSaveable(toolId) { mutableStateOf("") }
    var author by rememberSaveable(toolId) { mutableStateOf("") }
    val selected = files.firstOrNull { it.file.absolutePath == selectedPath }
    val validOptions = when (toolId) {
        "extract_pages", "remove_pages" -> range.isNotBlank()
        "watermark", "add_text" -> text.isNotBlank()
        "protect" -> password.length >= 4 && password == confirmation
        "unlock" -> password.isNotBlank()
        "page_numbers" -> (startNumber.toIntOrNull() ?: 0) > 0
        "metadata" -> title.isNotBlank() || author.isNotBlank()
        else -> true
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, if (english) "Back" else "Voltar") }
            Column(Modifier.weight(1f)) {
                Text(if (english) copy.titleEn else copy.titlePt, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(if (english) "Dedicated tool workspace" else "Espaço de trabalho da ferramenta", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            Icon(Icons.Default.MoreVert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 17.dp)) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(62.dp).background(
                            Brush.linearGradient(listOf(Color(0xFF0CC9E3), Color(0xFF2875E7))),
                            RoundedCornerShape(19.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(copy.icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.width(15.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (english) copy.titleEn else copy.titlePt, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(if (english) copy.descEn else copy.descPt, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }

            Spacer(Modifier.height(17.dp))
            when (toolId) {
                "rotate" -> {
                    Text(if (english) "ROTATION" else "ROTAÇÃO", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(9.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(90, 180, 270).forEach { degree ->
                            Card(
                                onClick = { degrees = degree },
                                modifier = Modifier.weight(1f).height(92.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = if (degrees == degree) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Icon(Icons.Default.RotateRight, null, tint = if (degrees == degree) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
                                    Text("$degree°", fontWeight = FontWeight.Bold)
                                    Text(if (degree == 90) (if (english) "Right" else "Direita") else if (degree == 180) (if (english) "Flip" else "Inverter") else (if (english) "Left" else "Esquerda"), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
                "extract_pages", "remove_pages" -> {
                    Text(if (english) "PAGE SELECTION" else "SELEÇÃO DE PÁGINAS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = range, onValueChange = { range = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '-' || ch.isWhitespace() } },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (english) "Page numbers and ranges" else "Números e intervalos") },
                        placeholder = { Text("1-3, 5, 8-10") },
                        leadingIcon = { Icon(Icons.Default.Layers, null) },
                        supportingText = { Text(if (english) "Example: 1-3, 5, 8-10 · pages start at 1" else "Exemplo: 1-3, 5, 8-10 · a contagem começa em 1") }
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(14.dp)) {
                        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(9.dp))
                            Text(
                                if (toolId == "remove_pages") {
                                    if (english) "Only the selected pages will be removed. The original file stays unchanged." else "Apenas as páginas indicadas serão removidas. O ficheiro original fica intacto."
                                } else {
                                    if (english) "A new PDF will contain only the pages you enter." else "Será criado um novo PDF apenas com as páginas indicadas."
                                },
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                "watermark", "add_text" -> {
                    Text(if (english) "CONTENT" else "CONTEÚDO", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = text, onValueChange = { text = it.take(90) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 3,
                        label = { Text(if (toolId == "watermark") (if (english) "Watermark text" else "Texto da marca de água") else (if (english) "Note to place on pages" else "Nota a colocar nas páginas")) },
                        placeholder = { Text(if (toolId == "watermark") "CONFIDENTIAL" else if (english) "Add a note..." else "Escreve uma nota...") }
                    )
                    Spacer(Modifier.height(10.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                        Box(Modifier.fillMaxWidth().height(88.dp).padding(12.dp), contentAlignment = Alignment.Center) {
                            Text(if (text.isBlank()) (if (english) "Your text preview" else "Pré-visualização do texto") else text, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                "protect", "unlock" -> {
                    Text(if (english) "DOCUMENT SECURITY" else "SEGURANÇA DO DOCUMENTO", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        label = { Text(if (english) "Password" else "Palavra-passe") },
                        visualTransformation = if (showPassword) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { IconButton(onClick = { showPassword = !showPassword }) { Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, null) } }
                    )
                    if (toolId == "protect") {
                        OutlinedTextField(
                            value = confirmation, onValueChange = { confirmation = it },
                            modifier = Modifier.fillMaxWidth(), singleLine = true,
                            label = { Text(if (english) "Confirm password" else "Confirmar palavra-passe") },
                            visualTransformation = PasswordVisualTransformation()
                        )
                        Text(if (english) "Use at least 4 characters and keep the password somewhere safe." else "Usa pelo menos 4 caracteres e guarda a palavra-passe num local seguro.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    } else {
                        Text(if (english) "The current password is required. The original is not changed." else "É necessária a palavra-passe atual. O original não será alterado.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
                "extract_text" -> {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(16.dp)) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.TextSnippet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(if (english) "Selectable text only" else "Apenas texto selecionável", fontWeight = FontWeight.Bold)
                                Text(if (english) "Scanned pages need OCR first. This export keeps the original PDF untouched." else "Páginas digitalizadas precisam de OCR primeiro. Esta exportação não altera o PDF original.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
                "to_images" -> {
                    Text(if (english) "IMAGE QUALITY" else "QUALIDADE DAS IMAGENS", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(dpi.toInt().toString() + " DPI", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Slider(value = dpi, onValueChange = { dpi = it }, valueRange = 96f..240f, steps = 5)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (english) "Smaller files" else "Ficheiros menores", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (english) "Sharper images" else "Imagens mais nítidas", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                "page_numbers" -> {
                    Text(if (english) "NUMBERING" else "NUMERAÇÃO", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = startNumber, onValueChange = { startNumber = it.filter(Char::isDigit).take(4) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (english) "Start at" else "Começar em") })
                    Text(if (english) "Numbers are placed at the bottom of each page." else "Os números são colocados no rodapé de cada página.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                "metadata" -> {
                    Text(if (english) "DOCUMENT DETAILS" else "DETALHES DO DOCUMENTO", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = title, onValueChange = { title = it.take(120) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (english) "Document title" else "Título do documento") })
                    OutlinedTextField(value = author, onValueChange = { author = it.take(100) }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (english) "Author" else "Autor") })
                }
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (english) "CHOOSE A PDF" else "ESCOLHE UM PDF", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onImport) { Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp)); Text(if (english) "Import" else "Importar") }
            }
            if (files.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(15.dp)) {
                    Row(Modifier.fillMaxWidth().clickable(onClick = onImport).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.FolderOpen, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (english) "Your library is empty" else "A tua biblioteca está vazia", fontWeight = FontWeight.Bold)
                            Text(if (english) "Import a PDF to start." else "Importa um PDF para começar.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            } else {
                LazyColumn(Modifier.heightIn(max = 210.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(files, key = { it.file.absolutePath }) { entry ->
                        val checked = selectedPath == entry.file.absolutePath
                        Card(
                            onClick = { selectedPath = entry.file.absolutePath },
                            colors = CardDefaults.cardColors(containerColor = if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.formattedSize, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                                RadioButton(selected = checked, onClick = { selectedPath = entry.file.absolutePath })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { selected?.let { onExecute(it.file, mapOf("range" to range, "degrees" to degrees.toString(), "text" to text, "password" to password, "dpi" to dpi.toInt().toString(), "start" to startNumber, "title" to title, "author" to author)) } },
                enabled = selected != null && validOptions,
                modifier = Modifier.fillMaxWidth().height(58.dp),
                shape = RoundedCornerShape(17.dp)
            ) {
                Icon(Icons.Default.Bolt, null)
                Spacer(Modifier.width(9.dp))
                Text(if (english) copy.actionEn else copy.actionPt, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Text(if (english) "Local processing · the source PDF is never overwritten" else "Processamento local · o PDF original nunca é substituído", modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 9.dp, bottom = 18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
        }
    }
}
