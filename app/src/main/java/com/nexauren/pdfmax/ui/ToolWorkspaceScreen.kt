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
import com.nexauren.pdfmax.data.PdfTranslationOperations
import com.nexauren.pdfmax.data.AdvancedPdfOperations
import com.nexauren.pdfmax.data.OfficeExportOperations
import com.nexauren.pdfmax.data.DocumentUtilityOperations
import com.nexauren.pdfmax.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private enum class ToolFlowStage { SELECT, PROCESSING, RESULT }

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
    var pageAfter by remember { mutableStateOf("1") }
    var metadataTitle by remember { mutableStateOf("") }
    var metadataAuthor by remember { mutableStateOf("") }
    var metadataSubject by remember { mutableStateOf("") }
    var metadataKeywords by remember { mutableStateOf("") }
    var formFieldName by remember { mutableStateOf("") }
    var formFieldValue by remember { mutableStateOf("") }
    var signatureStrokes by remember { mutableStateOf<List<List<Pair<Float, Float>>>>(emptyList()) }
    var sourceLanguage by remember { mutableStateOf("pt") }
    var targetLanguage by remember { mutableStateOf("en") }
    var rotation by remember { mutableIntStateOf(90) }
    var exportFormat by remember { mutableStateOf("Word") }
    var showAll by remember { mutableStateOf(false) }
    var awaitingPermissionReturn by remember { mutableStateOf(false) }

    // A tool is a three-stage workflow: selection, visible processing, then result/save.
    var flowStage by remember(tool.title) { mutableStateOf(ToolFlowStage.SELECT) }
    var progressElapsedSeconds by remember(tool.title) { mutableIntStateOf(0) }
    var progressLabel by remember(tool.title) { mutableStateOf("") }
    var resultFailed by remember(tool.title) { mutableStateOf(false) }
    var resultSaved by remember(tool.title) { mutableStateOf(false) }
    var saving by remember(tool.title) { mutableStateOf(false) }

    LaunchedEffect(flowStage) {
        if (flowStage == ToolFlowStage.PROCESSING) {
            progressElapsedSeconds = 0
            while (flowStage == ToolFlowStage.PROCESSING) {
                delay(1_000)
                if (flowStage == ToolFlowStage.PROCESSING) progressElapsedSeconds += 1
            }
        }
    }

    fun refresh(forceRefresh: Boolean = false) {
        access = DocumentScanner.hasAccess(context)
        if (!access) {
            documents = emptyList()
            loading = false
            return
        }
        if (loading) return
        // Restore the saved library first; processing tools never wait for a full scan to show it.
        DocumentScanner.cachedDocuments().takeIf { it.isNotEmpty() }?.let { documents = it }
        loading = true
        scope.launch {
            val result = runCatching {
                val savedIndex = withContext(Dispatchers.IO) { DocumentScanner.loadPersistent(context) }
                if (documents.isEmpty() && savedIndex.isNotEmpty()) documents = savedIndex
                withContext(Dispatchers.IO) { DocumentScanner.scan(context, forceRefresh) }
            }
            result.onSuccess { indexed ->
                documents = indexed
                if (selectedPath == null || indexed.none { it.file.absolutePath == selectedPath }) {
                    selectedPath = indexed.firstOrNull { it.kind == DocumentKind.PDF }?.file?.absolutePath
                        ?: indexed.firstOrNull()?.file?.absolutePath
                }
            }.onFailure {
                outputMessage = (if (english) "Could not index storage: " else "Não foi possível indexar o armazenamento: ") + (it.message ?: "erro")
            }
            loading = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh(true) }
    fun requestAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            awaitingPermissionReturn = true
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply { data = Uri.parse("package:" + context.packageName) }
            runCatching { context.startActivity(intent) }.onFailure {
                runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            }
        } else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(DocumentScanner.REFRESH_INTERVAL_MS)
            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) refresh()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && awaitingPermissionReturn) {
                awaitingPermissionReturn = false
                refresh(forceRefresh = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selected = documents.firstOrNull { it.file.absolutePath == selectedPath }?.file
    val needsMultiple = tool.title in setOf("Juntar PDF", "Comparar PDFs", "Criar ZIP", "Imagem para PDF", "Digitalizar e OCR")
    val minimumSelection = if (tool.title == "Juntar PDF" || tool.title == "Comparar PDFs") 2 else 1
    val eligible = when (tool.title) {
        "Digitalizar e OCR", "Imagem para PDF" -> documents.filter { it.kind == DocumentKind.IMAGE }
        "PDF para imagem", "Extrair imagens", "Juntar PDF", "Dividir PDF", "Comprimir PDF", "Proteger PDF", "Rodar páginas", "Adicionar texto", "Editar PDF", "Marca de água", "Numerar páginas", "Assinar PDF", "Reparar PDF", "Ver propriedades", "Organizar páginas", "Remover páginas", "Traduzir PDF", "Preencher formulários", "Imprimir PDF", "Partilhar PDF", "Extrair texto PDF", "Extrair páginas", "Adicionar página em branco", "Editar metadados" -> documents.filter { it.kind == DocumentKind.PDF }
        "Converter PDF" -> documents.filter { it.kind == DocumentKind.PDF || it.extension in setOf("docx", "xlsx", "pptx", "jpg", "jpeg", "png") }
        "Criar ZIP", "Detetar duplicados" -> documents
        else -> documents
    }
    val visible = eligible.filter { item -> query.isBlank() || item.name.contains(query, true) || item.file.parent.orEmpty().contains(query, true) || item.extension.contains(query.removePrefix("."), true) }.take(if (showAll) 1000 else 40)

    fun execute() {
        val file = selected
        if (tool.title !in setOf("Criar PDF", "Detetar duplicados") && !needsMultiple && file == null) {
            outputMessage = if (english) "Select a document from the internal library first." else "Escolha primeiro um documento na biblioteca interna."
            return
        }
        if (needsMultiple && selectedPaths.size < minimumSelection) {
            outputMessage = if (english) "Select at least " + minimumSelection + " compatible files." else "Selecione pelo menos " + minimumSelection + " ficheiro(s) compatível(eis)."
            return
        }
        scope.launch {
            running = true
            flowStage = ToolFlowStage.PROCESSING
            progressElapsedSeconds = 0
            progressLabel = if (english) "Preparing selected files…" else "A preparar os ficheiros selecionados…"
            resultFailed = false
            resultSaved = false
            outputMessage = null
            resultFiles = emptyList()
            val result = runCatching {
                progressLabel = if (english) "Running " + localizedToolTitle(tool.title, true) + "…" else "A executar " + tool.title + "…"
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
                        "Imagem para PDF" -> listOf(AdvancedPdfOperations.imagesToPdf(context, documents.filter { it.file.absolutePath in selectedPaths }.map { it.file }))
                        "Digitalizar e OCR" -> listOf(PdfOperations.recognizeImagesToPdf(context, documents.filter { it.file.absolutePath in selectedPaths }.map { it.file }))
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
                        "Extrair páginas" -> listOf(AdvancedPdfOperations.extractPages(context, file!!, pageOrder.split(",").mapNotNull { it.trim().toIntOrNull() }))
                        "Adicionar página em branco" -> listOf(AdvancedPdfOperations.insertBlankPage(context, file!!, pageAfter.toIntOrNull() ?: 1))
                        "Editar metadados" -> listOf(AdvancedPdfOperations.editMetadata(context, file!!, metadataTitle, metadataAuthor, metadataSubject, metadataKeywords))
                        "Extrair texto PDF" -> listOf(DocumentUtilityOperations.extractTextToFile(context, file!!))
                        "Criar ZIP" -> listOf(DocumentUtilityOperations.createZip(documents.filter { it.file.absolutePath in selectedPaths }.map { it.file }))
                        "Detetar duplicados" -> {
                            val groups = DocumentUtilityOperations.findDuplicateGroups(documents.map { it.file })
                            outputMessage = DocumentUtilityOperations.duplicateReport(groups)
                            emptyList()
                        }
                        "Remover páginas" -> listOf(AdvancedPdfOperations.removePages(context, file!!, removePageNumbers.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()))
                        "Preencher formulários" -> listOf(AdvancedPdfOperations.fillFormField(context, file!!, formFieldName, formFieldValue))
                        "Traduzir PDF" -> listOf(PdfTranslationOperations.translatePdf(context, file!!, sourceLanguage, targetLanguage))
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
            progressLabel = if (english) "Preparing the result…" else "A preparar o resultado…"
            if (result.isSuccess) {
                val produced = result.getOrThrow()
                if (produced.isNotEmpty()) {
                    // Keep generated files private until the user confirms Save on the result screen.
                    val staged = runCatching {
                        withContext(Dispatchers.IO) {
                            val stagingDirectory = File(context.cacheDir, "pdfmax_pending_" + System.currentTimeMillis())
                            check(stagingDirectory.mkdirs() || stagingDirectory.isDirectory) {
                                "Não foi possível preparar a pasta temporária."
                            }
                            val copies = produced.map { output ->
                                output.copyTo(File(stagingDirectory, output.name), overwrite = true)
                            }
                            produced.forEach { it.delete() }
                            copies
                        }
                    }
                    if (staged.isSuccess) {
                        resultFiles = staged.getOrThrow()
                        resultSaved = false
                        outputMessage = if (english) {
                            "Your result is ready. Review the files below and save them to your device."
                        } else {
                            "O resultado está pronto. Reveja os ficheiros abaixo e guarde-os no dispositivo."
                        }
                    } else {
                        // If temporary staging fails, preserve the operation's original saved output.
                        resultFiles = produced
                        resultSaved = true
                        outputMessage = if (english) {
                            "Completed. The output remains in its original location: " + (produced.firstOrNull()?.parent.orEmpty())
                        } else {
                            "Concluído. O resultado permanece na localização original: " + (produced.firstOrNull()?.parent.orEmpty())
                        }
                    }
                }
                resultFailed = false
            } else {
                val failure = result.exceptionOrNull()
                outputMessage = (if (english) "Could not complete: " else "Não foi possível concluir: ") +
                    (failure?.message ?: if (english) "Unknown error" else "erro desconhecido")
                resultFailed = true
            }
            flowStage = ToolFlowStage.RESULT
            running = false
        }
    }

    fun saveResults() {
        if (saving || resultSaved || resultFiles.isEmpty()) return
        scope.launch {
            saving = true
            val pendingResults = resultFiles.toList()
            val saveAttempt = runCatching {
                withContext(Dispatchers.IO) {
                    val outputDirectory = PdfOperations.outputDirectory()
                    check(outputDirectory.exists() || outputDirectory.mkdirs()) {
                        "Não foi possível criar Documentos/PDF Max."
                    }
                    resultFiles.map { pending ->
                        var destination = File(outputDirectory, pending.name)
                        if (destination.exists()) {
                            val base = pending.nameWithoutExtension
                            val extension = pending.extension
                            var suffix = 2
                            do {
                                val candidateName = if (extension.isBlank()) "$base ($suffix)" else "$base ($suffix).$extension"
                                destination = File(outputDirectory, candidateName)
                                suffix++
                            } while (destination.exists())
                        }
                        pending.copyTo(destination, overwrite = false)
                    }
                }
            }
            if (saveAttempt.isSuccess) {
                val savedFiles = saveAttempt.getOrThrow()
                resultFiles = savedFiles
                resultSaved = true
                outputMessage = if (english) {
                    "Saved successfully in Documents/PDF Max."
                } else {
                    "Guardado com sucesso em Documentos/PDF Max."
                }
                withContext(Dispatchers.IO) {
                    pendingResults.mapNotNull { it.parentFile }
                        .distinctBy { it.absolutePath }
                        .filter { it.name.startsWith("pdfmax_pending_") }
                        .forEach { it.deleteRecursively() }
                }
                DocumentScanner.invalidate()
                refresh(forceRefresh = true)
            } else {
                outputMessage = (if (english) "Could not save: " else "Não foi possível guardar: ") +
                    (saveAttempt.exceptionOrNull()?.message ?: "erro desconhecido")
            }
            saving = false
        }
    }

    if (flowStage == ToolFlowStage.PROCESSING) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(82.dp).clip(RoundedCornerShape(24.dp))
                    .background(tool.color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, null, tint = tool.color, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(24.dp))
            Text(
                if (english) "STEP 2 OF 3 · PROCESSING" else "ETAPA 2 DE 3 · A PROCESSAR",
                color = tool.color, fontSize = 11.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                if (english) localizedToolTitle(tool.title, true) else tool.title,
                color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                progressLabel,
                color = MaxMuted, fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                color = tool.color,
                strokeWidth = 4.dp
            )
            Spacer(Modifier.height(20.dp))
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = tool.color,
                trackColor = Color(0xFF203653)
            )
            Spacer(Modifier.height(18.dp))
            val elapsedMinutes = progressElapsedSeconds / 60
            val elapsedSeconds = progressElapsedSeconds % 60
            val elapsed = String.format(java.util.Locale.getDefault(), "%02d:%02d", elapsedMinutes, elapsedSeconds)
            Text(
                (if (english) "Elapsed time: " else "Tempo decorrido: ") + elapsed,
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(11.dp))
            val fileCount = when {
                tool.title == "Detetar duplicados" -> documents.size
                needsMultiple -> selectedPaths.size
                selected != null -> 1
                else -> 0
            }
            Text(
                if (english) "Working with $fileCount file(s). Large or scanned PDFs may take longer. Keep PDF Max open."
                else "A processar $fileCount ficheiro(s). PDFs grandes ou digitalizados podem demorar mais. Mantenha o PDF Max aberto.",
                color = MaxMuted, fontSize = 11.sp, lineHeight = 16.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(18.dp))
            ToolPanel(if (english) "What is happening now" else "O que está a acontecer", tool.color) {
                InfoLine(
                    if (english) "PDF Max is processing your selection in the background. This indicator stays visible until the operation finishes."
                    else "O PDF Max está a processar a sua seleção em segundo plano. Este indicador permanece visível até a operação terminar.",
                    Icons.Default.Sync
                )
            }
        }
    } else if (flowStage == ToolFlowStage.RESULT) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(17.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(17.dp))
                        .background(if (resultFailed) Color(0xFFB83D55) else Color(0xFF159B79)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (resultFailed) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                        null, tint = Color.White, modifier = Modifier.size(31.dp)
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (english) "STEP 3 OF 3 · RESULT" else "ETAPA 3 DE 3 · RESULTADO",
                        color = tool.color, fontSize = 10.sp, fontWeight = FontWeight.Bold
                    )
                    Text(
                        when {
                            resultFailed -> if (english) "Operation failed" else "Não foi possível concluir"
                            resultFiles.isNotEmpty() && !resultSaved -> if (english) "Ready to save" else "Pronto para guardar"
                            resultFiles.isNotEmpty() && resultSaved -> if (english) "Files saved" else "Ficheiros guardados"
                            else -> if (english) "Operation completed" else "Operação concluída"
                        },
                        color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            outputMessage?.let { message ->
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
                        .background(Color(0xFF112541))
                        .padding(14.dp)
                ) {
                    Text(
                        message, color = Color.White, fontSize = 11.sp, lineHeight = 17.sp
                    )
                }
            }
            if (resultFiles.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                ToolPanel(
                    if (resultSaved) {
                        if (english) "Saved output" else "Resultado guardado"
                    } else {
                        if (english) "Review your result" else "Rever o resultado"
                    },
                    tool.color
                ) {
                    resultFiles.forEach { produced ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (produced.extension.equals("pdf", true)) Icons.Default.PictureAsPdf else Icons.Default.InsertDriveFile,
                                null, tint = tool.color, modifier = Modifier.size(25.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    produced.name, color = Color.White,
                                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    if (resultSaved) produced.absolutePath
                                    else (if (english) "Temporary result · " else "Resultado temporário · ") +
                                        String.format(java.util.Locale.getDefault(), "%.1f KB", produced.length() / 1024f),
                                    color = MaxMuted, fontSize = 9.sp, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Icon(
                                if (resultSaved) Icons.Default.CheckCircle else Icons.Default.Schedule,
                                null, tint = if (resultSaved) Color(0xFF26D39A) else tool.color,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (resultFiles.isNotEmpty() && !resultSaved && !resultFailed) {
                Button(
                    onClick = { saveResults() },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = tool.color),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Save, null, tint = Color.White)
                    Spacer(Modifier.width(9.dp))
                    Text(
                        if (saving) {
                            if (english) "Saving…" else "A guardar…"
                        } else {
                            if (english) "Save to Documents / PDF Max" else "Guardar em Documentos / PDF Max"
                        },
                        color = Color.White, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    flowStage = ToolFlowStage.SELECT
                    outputMessage = null
                    resultFiles = emptyList()
                    resultFailed = false
                    resultSaved = false
                    selectedPaths = emptySet()
                    selectedPath = null
                },
                modifier = Modifier.fillMaxWidth().height(49.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF31547A)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (resultFailed) {
                        if (english) "Try again" else "Tentar novamente"
                    } else {
                        if (english) "New operation" else "Nova operação"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    } else {

    Column(Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(53.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(tool.color, tool.color.copy(alpha = 0.55f)))), contentAlignment = Alignment.Center) {
                Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(29.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(localizedToolTitle(tool.title, english), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text(localizedToolSubtitle(tool.title, english), color = MaxMuted, fontSize = 10.sp)
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
                if (tool.title == "Detetar duplicados") {
                    InfoLine(if (english) "PDF Max will scan the complete internal library and compare SHA-256 hashes. It will never delete files automatically." else "O PDF Max vai analisar toda a biblioteca interna e comparar hashes SHA-256. Nunca elimina ficheiros automaticamente.", Icons.Default.ContentCopy)
                } else if (needsMultiple) {
                    Text(
                        when (tool.title) {
                            "Juntar PDF", "Comparar PDFs" -> if (english) "Choose at least two PDFs" else "Escolha pelo menos dois PDFs"
                            "Imagem para PDF" -> if (english) "Choose the images to combine into one PDF" else "Escolha as imagens que serão reunidas num único PDF"
                            "Digitalizar e OCR" -> if (english) "Choose one or more scanned images for batch OCR" else "Escolha uma ou várias imagens digitalizadas para OCR em lote"
                            else -> if (english) "Choose the files to add to the ZIP" else "Escolha os ficheiros a adicionar ao ZIP"
                        },
                        color = MaxMuted, fontSize = 10.sp
                    )
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
                if (eligible.size > 8) TextButton(onClick = { showAll = !showAll }) { Text(if (showAll) { if (english) "Show fewer" else "Mostrar menos" } else { if (english) "Show more files (" + eligible.size + ")" else "Mostrar mais ficheiros (" + eligible.size + ")" }, color = MaxCyan) }
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
                    "Extrair páginas" -> {
                        OutlinedTextField(pageOrder, { pageOrder = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "Pages to extract (e.g. 1,3,5)" else "Páginas a extrair (ex.: 1,3,5)") }, singleLine = true, colors = toolFieldColors())
                        InfoLine(if (english) "A new PDF is created with only the pages you list, in the same order." else "É criado um novo PDF apenas com as páginas indicadas, na mesma ordem.", Icons.Default.Description)
                    }
                    "Adicionar página em branco" -> {
                        NumberField(if (english) "Insert after page (0 = first page)" else "Inserir depois da página (0 = primeira página)", pageAfter) { pageAfter = it }
                        InfoLine(if (english) "A blank A4 page is inserted at the selected position. The original PDF stays unchanged." else "É inserida uma página A4 em branco na posição indicada. O PDF original não é alterado.", Icons.Default.NoteAdd)
                    }
                    "Editar metadados" -> {
                        OutlinedTextField(metadataTitle, { metadataTitle = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "Document title" else "Título do documento") }, singleLine = true, colors = toolFieldColors())
                        OutlinedTextField(metadataAuthor, { metadataAuthor = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "Author" else "Autor") }, singleLine = true, colors = toolFieldColors())
                        OutlinedTextField(metadataSubject, { metadataSubject = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "Subject" else "Assunto") }, singleLine = true, colors = toolFieldColors())
                        OutlinedTextField(metadataKeywords, { metadataKeywords = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "Keywords" else "Palavras-chave") }, singleLine = true, colors = toolFieldColors())
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
                                val capturedDocument = IndexedDocument(captured)
                                documents = (documents.filterNot { it.file.absolutePath == captured.absolutePath } + capturedDocument)
                                    .sortedByDescending { it.modified }
                                selectedPath = captured.absolutePath
                                selectedPaths = selectedPaths + captured.absolutePath
                            }
                        }
                        Spacer(Modifier.height(9.dp))
                        InfoLine(if (english) "Capture pages with the built-in camera or select several images above. PDF Max recognizes them locally and creates one searchable PDF." else "Capture páginas com a câmara integrada ou selecione várias imagens acima. O PDF Max reconhece o texto localmente e cria um único PDF pesquisável.", Icons.Default.TextFields)
                    }
                    "Imagem para PDF" -> InfoLine(if (english) "Selected images are combined into one multi-page A4 PDF." else "As imagens selecionadas são reunidas num PDF A4 com várias páginas.", Icons.Default.Image)
                    "Extrair texto PDF" -> InfoLine(if (english) "Extract selectable PDF text into a UTF-8 TXT file, saved in Documents/PDF Max." else "Extraia o texto selecionável do PDF para um ficheiro TXT em Documentos/PDF Max.", Icons.Default.TextSnippet)
                    "Criar ZIP" -> InfoLine(if (english) "Create one ZIP archive from selected documents. Original files remain untouched." else "Crie um arquivo ZIP com os documentos selecionados. Os ficheiros originais permanecem intactos.", Icons.Default.FolderZip)
                    "Detetar duplicados" -> InfoLine(if (english) "Compares exact file contents with SHA-256, not just names or file sizes." else "Compara o conteúdo exato através de SHA-256, não apenas os nomes ou tamanhos.", Icons.Default.ContentCopy)
                    "Juntar PDF" -> InfoLine(if (english) "Selected PDFs are combined in the order shown in the library." else "Os PDFs selecionados são combinados pela ordem apresentada na biblioteca.", Icons.Default.MergeType)
                    "Comparar PDFs" -> InfoLine(if (english) "Compare extracted text and show lines that appear added or removed." else "Compare o texto extraído e mostre as linhas que parecem adicionadas ou removidas.", Icons.Default.CompareArrows)
                    "Traduzir PDF" -> {
                        val languages = listOf(
                            "Português" to "pt",
                            "English" to "en",
                            "Français" to "fr",
                            "Español" to "es",
                            "Deutsch" to "de",
                            "Italiano" to "it"
                        )
                        Text(if (english) "Source language" else "Idioma de origem", color = MaxMuted, fontSize = 10.sp)
                        languages.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { language ->
                                    ChoiceChip(language.first, sourceLanguage == language.second, { sourceLanguage = language.second }, tool.color)
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(if (english) "Target language" else "Idioma de destino", color = MaxMuted, fontSize = 10.sp)
                        languages.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { language ->
                                    ChoiceChip(language.first, targetLanguage == language.second, { targetLanguage = language.second }, tool.color)
                                }
                            }
                        }
                        InfoLine(
                            if (english) "Translation runs on this device. The first use downloads the selected language models over Wi-Fi." else "A tradução é executada neste dispositivo. Na primeira utilização, os modelos de idioma são descarregados por Wi-Fi.",
                            Icons.Default.Translate
                        )
                    }
                    "Preencher formulários" -> InfoLine(if (english) "Fill existing AcroForm text fields in the selected PDF." else "Preencha campos AcroForm de texto existentes no PDF selecionado.", Icons.Default.EditNote)
                    "Organizar páginas" -> InfoLine(if (english) "Reorder PDF pages using the comma-separated page order above." else "Reordene páginas do PDF com a ordem de números separada por vírgulas acima.", Icons.Default.GridView)
                    "Remover páginas" -> InfoLine(if (english) "Remove the selected page numbers and save a new PDF." else "Remova as páginas indicadas e guarde um novo PDF.", Icons.Default.Delete)
                    else -> InfoLine(if (english) "Choose a source document to continue." else "Escolha um documento de origem para continuar.", tool.icon)
                }
            }

            Button(
                onClick = { execute() },
                enabled = !running && (selected != null || tool.title in setOf("Criar PDF", "Detetar duplicados") || needsMultiple) && (if (needsMultiple) selectedPaths.size >= minimumSelection else true),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = tool.color),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (running) CircularProgressIndicator(Modifier.size(19.dp), color = Color.White, strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                Spacer(Modifier.width(9.dp))
                Text(if (running) { if (english) "Processing…" else "A processar…" } else { if (english) "Start " + localizedToolTitle(tool.title, true) else "Iniciar " + tool.title }, color = Color.White, fontWeight = FontWeight.Bold)
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
