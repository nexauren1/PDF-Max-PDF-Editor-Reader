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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexauren.pdfmax.data.DocumentKind
import com.nexauren.pdfmax.data.DocumentScanner
import com.nexauren.pdfmax.data.IndexedDocument
import com.nexauren.pdfmax.data.PdfOperations
import com.nexauren.pdfmax.data.DocumentActions
import com.nexauren.pdfmax.data.AdvancedPdfOperations
import com.nexauren.pdfmax.data.OfficeExportOperations
import com.nexauren.pdfmax.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ToolWorkspaceScreen(tool: ToolSpec, english: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var access by remember { mutableStateOf(DocumentScanner.hasAccess(context)) }
    var documents by remember { mutableStateOf<List<IndexedDocument>>(emptyList()) }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var outputMessage by remember { mutableStateOf<String?>(null) }
    var resultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var textOption by remember { mutableStateOf(if (tool.title == "Assinar PDF") "Assinado digitalmente" else "") }
    var password by remember { mutableStateOf("") }
    var firstPage by remember { mutableStateOf("1") }
    var lastPage by remember { mutableStateOf("3") }
    var pageOrder by remember { mutableStateOf("1,2,3") }
    var removePageNumbers by remember { mutableStateOf("2") }
    var formFieldName by remember { mutableStateOf("") }
    var formFieldValue by remember { mutableStateOf("") }
    var signatureStrokes by remember { mutableStateOf<List<List<Pair<Float, Float>>>>(emptyList()) }
    var rotation by remember { mutableIntStateOf(90) }
    var exportFormat by remember { mutableStateOf("Word") }
    var showAll by remember { mutableStateOf(false) }

    fun refresh() {
        access = DocumentScanner.hasAccess(context)
        if (!access) return
        loading = true
        scope.launch {
            documents = withContext(Dispatchers.IO) { DocumentScanner.scan(context) }
            if (selectedPath == null) selectedPath = documents.firstOrNull { it.kind == DocumentKind.PDF }?.file?.absolutePath
                ?: documents.firstOrNull()?.file?.absolutePath
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:" + context.packageName) }
            runCatching { context.startActivity(intent) }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    LaunchedEffect(Unit) { refresh() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selected = documents.firstOrNull { it.file.absolutePath == selectedPath }?.file
    val needsTwo = tool.title == "Juntar PDF" || tool.title == "Comparar PDFs"
    val eligible = when (tool.title) {
        "Digitalizar e OCR", "Imagem para PDF" -> documents.filter { it.kind == DocumentKind.IMAGE }
        "PDF para imagem", "Extrair imagens", "Juntar PDF", "Dividir PDF", "Comprimir PDF", "Proteger PDF", "Rodar páginas", "Adicionar texto", "Editar PDF", "Marca de água", "Numerar páginas", "Assinar PDF", "Reparar PDF", "Ver propriedades", "Organizar páginas", "Remover páginas", "Traduzir PDF", "Preencher formulários", "Imprimir PDF", "Partilhar PDF" -> documents.filter { it.kind == DocumentKind.PDF }
        "Converter PDF" -> documents.filter { it.kind == DocumentKind.PDF || it.extension in setOf("docx", "xlsx", "pptx", "jpg", "jpeg", "png") }
        else -> documents
    }
    val visible = eligible.filter { it.name.contains(query, true) }.take(if (showAll) 100 else 6)

    fun execute() {
        val file = selected
        if (tool.title != "Criar PDF" && file == null) {
            outputMessage = if (english) "Select a document from the internal library first." else "Escolha primeiro um documento na biblioteca interna."
            return
        }
        if (needsTwo && selectedPaths.size < 2) {
            outputMessage = if (english) "Select at least two PDF files." else "Selecione pelo menos dois ficheiros PDF."
            return
        }
        scope.launch {
            running = true
            outputMessage = null
            resultFiles = emptyList()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when (tool.title) {
                        "Juntar PDF" -> listOf(PdfOperations.merge(context, documents.filter { it.file.absolutePath in selectedPaths }.map { it.file }))
                        "Dividir PDF" -> PdfOperations.split(context, file!!, firstPage.toIntOrNull() ?: 1, lastPage.toIntOrNull() ?: 1)
                        "Comprimir PDF" -> listOf(PdfOperations.compress(context, file!!))
                        "Proteger PDF" -> listOf(PdfOperations.protect(context, file!!, password))
                        "Rodar páginas" -> listOf(PdfOperations.rotate(context, file!!, rotation))
                        "Editar PDF", "Adicionar texto" -> listOf(PdfOperations.addText(context, file!!, textOption))
                        "Assinar PDF" -> listOf(AdvancedPdfOperations.addHandwrittenSignature(context, file!!, signatureStrokes))
                        "Marca de água" -> listOf(PdfOperations.addText(context, file!!, textOption, watermark = true))
                        "Numerar páginas" -> listOf(PdfOperations.addText(context, file!!, "", numbering = true))
                        "Reparar PDF" -> listOf(PdfOperations.repair(context, file!!))
                        "PDF para imagem" -> PdfOperations.pdfToImages(context, file!!)
                        "Imagem para PDF" -> listOf(PdfOperations.imageToPdf(context, file!!))
                        "Digitalizar e OCR" -> listOf(PdfOperations.recognizeImageToPdf(context, file!!))
                        "Extrair imagens" -> PdfOperations.extractImages(context, file!!)
                        "Ver propriedades" -> {
                            outputMessage = PdfOperations.getProperties(context, file!!)
                            emptyList()
                        }
                        "Comparar PDFs" -> {
                            val pair = documents.filter { it.file.absolutePath in selectedPaths }.take(2)
                            outputMessage = PdfOperations.compare(context, pair[0].file, pair[1].file)
                            emptyList()
                        }
                        "Converter PDF" -> when {
                            file!!.extension.lowercase() in setOf("docx", "xlsx", "pptx", "txt", "csv", "rtf", "md") -> listOf(AdvancedPdfOperations.officeToPdf(context, file))
                            file.extension.equals("pdf", true) && exportFormat == "Word" -> listOf(PdfOperations.pdfToDocx(context, file))
                            file.extension.equals("pdf", true) && exportFormat == "Excel" -> listOf(AdvancedPdfOperations.pdfToExcel(context, file))
                            file.extension.equals("pdf", true) && exportFormat == "PowerPoint" -> listOf(OfficeExportOperations.pdfToPowerPoint(context, file))
                            file.extension.lowercase() in setOf("jpg", "jpeg", "png") -> listOf(PdfOperations.imageToPdf(context, file))
                            else -> error(if (english) "This source/target format is not supported by the current converter." else "Este formato de origem/destino ainda não é suportado pelo conversor.")
                        }
                        "Criar PDF" -> listOf(PdfOperations.createPdfFromText(context, "Novo_Documento", textOption.ifBlank { "Documento criado com PDF Max" }))
                        "Organizar páginas" -> listOf(AdvancedPdfOperations.reorderPages(context, file!!, pageOrder.split(",").mapNotNull { it.trim().toIntOrNull() }))
                        "Remover páginas" -> listOf(AdvancedPdfOperations.removePages(context, file!!, removePageNumbers.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()))
                        "Preencher formulários" -> listOf(AdvancedPdfOperations.fillFormField(context, file!!, formFieldName, formFieldValue))
                        "Traduzir PDF" -> error(if (english) "Translation service is not configured. Your document has not been uploaded anywhere." else "O serviço de tradução não está configurado. O documento não foi enviado para lado nenhum.")
                        "Preencher formulários" -> error(if (english) "The form editor is not available yet." else "O editor de formulários ainda não está disponível.")
                        "Imprimir PDF" -> {
                            withContext(Dispatchers.Main) { DocumentActions.print(context, file!!) }
                            emptyList()
                        }
                        "Partilhar PDF" -> {
                            val sharedCopy = withContext(Dispatchers.IO) { DocumentActions.createShareCopy(context, file!!) }
                            withContext(Dispatchers.Main) { DocumentActions.share(context, sharedCopy) }
                            emptyList()
                        }
                        else -> error(if (english) "This tool is not connected yet." else "Esta ferramenta ainda não está ligada.")
                    }
                }
            }
            result.onSuccess { produced ->
                resultFiles = produced
                if (produced.isNotEmpty()) outputMessage = if (english) "Completed. Saved in Documents/PDF Max." else "Concluído. Guardado em Documentos/PDF Max."
                refresh()
            }.onFailure {
                outputMessage = (if (english) "Could not complete: " else "Não foi possível concluir: ") + (it.message ?: "erro desconhecido")
            }
            running = false
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(53.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(tool.color, tool.color.copy(alpha = 0.55f)))), contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(29.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(tool.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(tool.subtitle, color = MaxMuted, fontSize = 10.sp)
            }
            Icon(Icons.Default.AutoAwesome, null, tint = tool.color)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(tool.color, MaxBlue, MaxViolet))))
        Spacer(Modifier.height(16.dp))

        if (!access) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF102541)).padding(16.dp)) {
                Icon(Icons.Default.FolderSpecial, null, tint = MaxCyan, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                Text(if (english) "Use the internal document library" else "Utilize a biblioteca interna de documentos", color = Color.White, fontWeight = FontWeight.Bold)
                Text(if (english) "PDF Max scans compatible files on your device. Grant access once to use the tools." else "O PDF Max analisa ficheiros compatíveis no seu dispositivo. Autorize o acesso uma vez para utilizar as ferramentas.", color = MaxMuted, fontSize = 11.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { requestAccess() }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FF1))) { Text(if (english) "Grant access" else "Permitir acesso") }
            }
        } else {
            ToolPanel(if (english) "1 · Select document" else "1 · Selecionar documento", tool.color) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaxCyan)
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (english) "Search your files" else "Pesquisar os seus ficheiros") }, colors = toolFieldColors())
                if (needsTwo) {
                    Text(if (english) "Choose two or more PDFs" else "Escolha dois ou mais PDFs", color = MaxMuted, fontSize = 10.sp)
                    visible.forEach { item ->
                        val checked = item.file.absolutePath in selectedPaths
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).clickable {
                            selectedPaths = if (checked) selectedPaths - item.file.absolutePath else selectedPaths + item.file.absolutePath
                        }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, { selectedPaths = if (it) selectedPaths + item.file.absolutePath else selectedPaths - item.file.absolutePath })
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.sizeLabel, color = MaxMuted, fontSize = 9.sp)
                            }
                        }
                    }
                } else {
                    visible.forEach { item ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (selectedPath == item.file.absolutePath) Color(0xFF17395F) else Color.Transparent).clickable { selectedPath = item.file.absolutePath }.padding(horizontal = 8.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (item.kind == DocumentKind.PDF) Icons.Default.PictureAsPdf else if (item.kind == DocumentKind.IMAGE) Icons.Default.Image else Icons.Default.Description, null, tint = tool.color)
                            Spacer(Modifier.width(9.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.file.parent.orEmpty() + " • " + item.sizeLabel, color = MaxMuted, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            RadioButton(selectedPath == item.file.absolutePath, { selectedPath = item.file.absolutePath }, colors = RadioButtonDefaults.colors(selectedColor = MaxCyan))
                        }
                    }
                    if (visible.isEmpty()) Text(if (english) "No compatible files were found. Check the file library." else "Não foram encontrados ficheiros compatíveis. Verifique a biblioteca.", color = MaxMuted, fontSize = 11.sp)
                }
                if (eligible.size > 6) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) "Mostrar menos" else "Mostrar mais ficheiros", color = MaxCyan) }
            }

            ToolPanel(if (english) "2 · Options" else "2 · Opções", tool.color) {
                when (tool.title) {
                    "Dividir PDF" -> {
                        NumberField(if (english) "First page" else "Primeira página", firstPage) { firstPage = it }
                        NumberField(if (english) "Last page" else "Última página", lastPage) { lastPage = it }
                    }
                    "Rodar páginas" -> {
                        Text(if (english) "Rotation" else "Rotação", color = MaxMuted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(90, 180, 270).forEach { deg -> ChoiceChip(deg.toString() + "°", rotation == deg, { rotation = deg }, tool.color) }
                        }
                    }
                    "Proteger PDF" -> OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "PDF password (min. 4 chars)" else "Senha do PDF (mín. 4 caracteres)") }, visualTransformation = PasswordVisualTransformation(), colors = toolFieldColors(), singleLine = true)
                    "Converter PDF" -> {
                        Text(if (english) "Export format" else "Formato de exportação", color = MaxMuted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Word", "Excel", "PowerPoint").forEach { format -> ChoiceChip(format, exportFormat == format, { exportFormat = format }, tool.color) }
                        }
                        Text(if (english) "PDF to Office keeps selectable text. Page layout, complex tables and images may need manual adjustment." else "A conversão para Office preserva o texto selecionável. O layout, tabelas complexas e imagens podem precisar de ajustes manuais.", color = MaxMuted, fontSize = 9.sp)
                    }
                    "Assinar PDF" -> SignaturePad(english) { signatureStrokes = it }
                    "Organizar páginas" -> {
                        OutlinedTextField(pageOrder, { pageOrder = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "New page order (e.g. 3,1,2)" else "Nova ordem das páginas (ex.: 3,1,2)") }, singleLine = true, colors = toolFieldColors())
                        InfoLine(if (english) "Only listed pages are kept, in the order entered." else "Apenas as páginas indicadas são mantidas, pela ordem escrita.", Icons.Default.GridView)
                    }
                    "Remover páginas" -> OutlinedTextField(removePageNumbers, { removePageNumbers = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "Pages to remove (e.g. 2,4)" else "Páginas a remover (ex.: 2,4)") }, singleLine = true, colors = toolFieldColors())
                    "Preencher formulários" -> {
                        OutlinedTextField(formFieldName, { formFieldName = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "PDF form field name" else "Nome do campo do formulário PDF") }, singleLine = true, colors = toolFieldColors())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(formFieldValue, { formFieldValue = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "Value to insert" else "Valor a inserir") }, minLines = 2, colors = toolFieldColors())
                    }
                    "Editar PDF", "Adicionar texto", "Marca de água", "Criar PDF" -> {
                        OutlinedTextField(textOption, { textOption = it }, modifier = Modifier.fillMaxWidth(), label = { Text(when (tool.title) {
                            "Marca de água" -> if (english) "Watermark text" else "Texto da marca de água"
                            "Criar PDF" -> if (english) "Document content" else "Conteúdo do documento"
                            else -> if (english) "Text to add to every page" else "Texto a adicionar a cada página"
                        }) }, minLines = 2, colors = toolFieldColors())
                    }
                    "Comprimir PDF" -> InfoLine(if (english) "PDFBox rewrites document streams without rasterizing pages." else "O PDFBox regrava os fluxos do documento sem rasterizar as páginas.", Icons.Default.Compress)
                    "Digitalizar e OCR" -> {
                        CameraCapturePanel(english) { captured ->
                            scope.launch {
                                documents = withContext(Dispatchers.IO) { DocumentScanner.scan(context) }
                                selectedPath = captured.absolutePath
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        InfoLine(if (english) "Capture a page here or choose an existing image above. OCR runs locally and creates a searchable PDF." else "Capture uma página aqui ou escolha uma imagem existente acima. O OCR é executado localmente e cria um PDF pesquisável.", Icons.Default.TextFields)
                    }
                    "Imagem para PDF" -> InfoLine(if (english) "The selected image will be placed on an A4 page." else "A imagem selecionada será colocada numa página A4.", Icons.Default.Image)
                    "Juntar PDF" -> InfoLine(if (english) "Selected PDFs are combined in the order shown in the library." else "Os PDFs selecionados são combinados pela ordem apresentada na biblioteca.", Icons.Default.MergeType)
                    "Comparar PDFs" -> InfoLine(if (english) "Compare extracted text and show lines that appear added or removed." else "Compare o texto extraído e mostre as linhas que parecem adicionadas ou removidas.", Icons.Default.CompareArrows)
                    "Traduzir PDF" -> InfoLine(if (english) "Translation is not enabled yet; the document remains local." else "A tradução ainda não está ativada; o documento permanece no dispositivo.", Icons.Default.Translate)
                    "Preencher formulários" -> InfoLine(if (english) "Fill existing AcroForm text fields in the selected PDF." else "Preencha campos AcroForm de texto existentes no PDF selecionado.", Icons.Default.EditNote)
                    "Organizar páginas" -> InfoLine(if (english) "Reorder PDF pages using the comma-separated page order above." else "Reordene páginas do PDF com a ordem de números separada por vírgulas acima.", Icons.Default.GridView)
                    "Remover páginas" -> InfoLine(if (english) "Remove the selected page numbers and save a new PDF." else "Remova as páginas indicadas e guarde um novo PDF.", Icons.Default.Delete)
                    else -> InfoLine(if (english) "Choose a source document to continue." else "Escolha um documento de origem para continuar.", tool.icon)
                }
            }

            Button(
                onClick = { execute() },
                enabled = !running && (selected != null || tool.title == "Criar PDF") && (if (needsTwo) selectedPaths.size >= 2 else true),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tool.color),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (running) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(9.dp))
                Text(if (running) { if (english) "Processing…" else "A processar…" } else { if (english) "Run " + tool.title else "Executar " + tool.title }, color = Color.White, fontWeight = FontWeight.Bold)
            }

            outputMessage?.let { message ->
                Spacer(Modifier.height(13.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Color(0xFF112541)).padding(13.dp)) {
                    Text(message, color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                    resultFiles.forEach { produced ->
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF26D39A), modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(produced.name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                Text(produced.absolutePath, color = MaxMuted, fontSize = 8.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(15.dp))
            Text(if (english) "Output folder: Documents / PDF Max" else "Pasta de saída: Documentos / PDF Max", color = MaxMuted, fontSize = 9.sp)
        }
    }
}

@Composable
private fun ToolPanel(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF0E203B)).padding(13.dp)) {
        Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun NumberField(label: String, value: String, change: (String) -> Unit) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, colors = toolFieldColors())
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, click: () -> Unit, accent: Color) {
    Box(Modifier.clip(CircleShape).background(if (selected) accent else Color(0xFF1A304F)).clickable(onClick = click).padding(horizontal = 14.dp, vertical = 9.dp)) {
        Text(label, color = Color.White, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun InfoLine(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaxCyan, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, color = MaxMuted, fontSize = 10.sp, lineHeight = 15.sp)
    }
}

@Composable
private fun toolFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = MaxBlue, unfocusedBorderColor = Color(0xFF304766),
    focusedLabelColor = MaxCyan, unfocusedLabelColor = MaxMuted,
    cursorColor = MaxCyan, focusedContainerColor = Color(0xFF0D1D35),
    unfocusedContainerColor = Color(0xFF0D1D35)
)
