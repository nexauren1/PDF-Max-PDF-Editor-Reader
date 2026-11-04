package com.nexauren.pdfmax.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.nexauren.pdfmax.data.PageNumberParser
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

    suspend fun discardPendingResults(files: List<File>) {
        withContext(Dispatchers.IO) {
            files.mapNotNull { it.parentFile }
                .distinctBy { it.absolutePath }
                .filter { it.name.startsWith("pdfmax_pending_") }
                .forEach { directory ->
                    runCatching { directory.deleteRecursively() }
                }
        }
    }

    LaunchedEffect(tool.title) {
        discardPendingResults(resultFiles)
        selectedPath = null
        selectedPaths = emptySet()
        query = ""
        showAll = false
        outputMessage = null
        resultFiles = emptyList()
        resultFailed = false
        resultSaved = false
        flowStage = ToolFlowStage.SELECT
        refresh()
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
        "PDF para imagem", "Extrair imagens", "Juntar PDF", "Dividir PDF", "Comprimir PDF", "Proteger PDF", "Rodar páginas", "Adicionar texto", "Editar PDF", "Marca de água", "Numerar páginas", "Assinar PDF", "Reparar PDF", "Ver propriedades", "Organizar páginas", "Remover páginas", "Traduzir PDF", "Preencher formulários", "Imprimir PDF", "Partilhar PDF", "Extrair texto PDF", "Extrair páginas", "Adicionar página em branco", "Editar metadados", "Achatar formulários", "Remover metadados", "Relatório do PDF" -> documents.filter { it.kind == DocumentKind.PDF }
        "Converter PDF" -> documents.filter { it.kind == DocumentKind.PDF || it.extension in setOf("docx", "xlsx", "pptx", "txt", "csv", "md", "log", "jpg", "jpeg", "png") }
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
                        "Dividir PDF" -> PdfOperations.split(context, file!!, firstPage.toIntOrNull() ?: error("Indique a primeira página com um número válido."), lastPage.toIntOrNull() ?: error("Indique a última página com um número válido."))
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
                            file!!.extension.lowercase() in setOf("docx", "xlsx", "pptx", "txt", "csv", "md", "log") -> listOf(AdvancedPdfOperations.officeToPdf(context, file))
                            file.extension.equals("pdf", true) && exportFormat == "Word" -> listOf(PdfOperations.pdfToDocx(context, file))
                            file.extension.equals("pdf", true) && exportFormat == "Excel" -> listOf(AdvancedPdfOperations.pdfToExcel(context, file))
                            file.extension.equals("pdf", true) && exportFormat == "PowerPoint" -> listOf(OfficeExportOperations.pdfToPowerPoint(context, file))
                            file.extension.lowercase() in setOf("jpg", "jpeg", "png") -> listOf(PdfOperations.imageToPdf(context, file))
                            else -> error(if (english) "This source/target format is not supported by the current converter." else "Este formato de origem/destino ainda não é suportado pelo conversor.")
                        }
                        "Criar PDF" -> listOf(PdfOperations.createPdfFromText(context, "Novo_Documento", textOption.ifBlank { "Documento criado com PDF Max" }))
                        "Organizar páginas" -> listOf(AdvancedPdfOperations.reorderPages(context, file!!, PageNumberParser.parse(pageOrder)))
                        "Extrair páginas" -> listOf(AdvancedPdfOperations.extractPages(context, file!!, PageNumberParser.parse(pageOrder)))
                        "Adicionar página em branco" -> listOf(AdvancedPdfOperations.insertBlankPage(context, file!!, pageAfter.toIntOrNull() ?: error("Indique uma posição de página válida.")))
                        "Editar metadados" -> listOf(AdvancedPdfOperations.editMetadata(context, file!!, metadataTitle, metadataAuthor, metadataSubject, metadataKeywords))
                        "Achatar formulários" -> listOf(AdvancedPdfOperations.flattenForms(context, file!!))
                        "Remover metadados" -> listOf(AdvancedPdfOperations.removeDocumentMetadata(context, file!!))
                        "Relatório do PDF" -> {
                            val extracted = PdfOperations.extractText(context, file!!)
                            val words = extracted.trim().split(Regex("\\s+")).count { it.isNotBlank() }
                            val lines = extracted.lineSequence().count { it.isNotBlank() }
                            outputMessage = PdfOperations.getProperties(context, file!!) +
                                "\nPalavras extraídas: " + words +
                                "\nLinhas de texto: " + lines +
                                "\nCaracteres: " + extracted.length +
                                if (extracted.isBlank()) "\nAviso: não foi encontrado texto selecionável; execute OCR se o documento for uma digitalização." else ""
                            emptyList()
                        }
                        "Extrair texto PDF" -> listOf(DocumentUtilityOperations.extractTextToFile(context, file!!))
                        "Criar ZIP" -> listOf(DocumentUtilityOperations.createZip(documents.filter { it.file.absolutePath in selectedPaths }.map { it.file }))
                        "Detetar duplicados" -> {
                            val groups = DocumentUtilityOperations.findDuplicateGroups(documents.map { it.file })
                            outputMessage = DocumentUtilityOperations.duplicateReport(groups)
                            emptyList()
                        }
                        "Remover páginas" -> listOf(AdvancedPdfOperations.removePages(context, file!!, PageNumberParser.parse(removePageNumbers).toSet()))
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

            if (tool.title == "Traduzir PDF" && resultFiles.isNotEmpty() && !resultFailed) {
                Spacer(Modifier.height(12.dp))
                ToolPanel(if (english) "Powered by Google Translate" else "Com tecnologia do Google Tradutor", tool.color) {
                    Text(
                        if (english) "This is an automatic translation. Review names, numbers and important terms before relying on it. Translation quality can vary by language."
                        else "Esta é uma tradução automática. Reveja nomes, números e termos importantes antes de utilizar o documento. A qualidade pode variar conforme os idiomas.",
                        color = MaxMuted, fontSize = 10.sp, lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text(
                        if (english) "THIS SERVICE MAY CONTAIN TRANSLATIONS POWERED BY GOOGLE. GOOGLE DISCLAIMS ALL WARRANTIES RELATED TO THE TRANSLATIONS, EXPRESS OR IMPLIED, INCLUDING ANY WARRANTIES OF ACCURACY, RELIABILITY, AND ANY IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT."
                        else "ESTE SERVIÇO PODE CONTER TRADUÇÕES COM TECNOLOGIA DO GOOGLE. A GOOGLE REJEITA TODAS AS GARANTIAS RELACIONADAS COM AS TRADUÇÕES, EXPRESSAS OU IMPLÍCITAS, INCLUINDO GARANTIAS DE EXATIDÃO, FIABILIDADE, COMERCIALIZAÇÃO, ADEQUAÇÃO A UM FIM ESPECÍFICO E NÃO INFRAÇÃO.",
                        color = MaxMuted, fontSize = 8.sp, lineHeight = 12.sp
                    )
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com")))
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
                    ) {
                        Text(
                            if (english) "Learn about Google Translate" else "Conhecer o Google Tradutor",
                            color = tool.color, fontSize = 10.sp
                        )
                    }
                }
            }
            if (tool.title == "Comprimir PDF" && selected != null && resultFiles.size == 1) {
                Spacer(Modifier.height(14.dp))
                val originalBytes = selected.length()
                val outputBytes = resultFiles.first().length()
                if (originalBytes > 0L) {
                    val difference = originalBytes - outputBytes
                    val changePercent = kotlin.math.abs(difference).toDouble() * 100.0 / originalBytes.toDouble()
                    val status = when {
                        difference > 0L -> if (english) "The file is ${String.format(java.util.Locale.getDefault(), "%.1f", changePercent)}% smaller." else "O ficheiro ficou ${String.format(java.util.Locale.getDefault(), "%.1f", changePercent)}% menor."
                        difference < 0L -> if (english) "The saved copy is ${String.format(java.util.Locale.getDefault(), "%.1f", changePercent)}% larger. This PDF could not be reduced by a lossless rewrite." else "A cópia ficou ${String.format(java.util.Locale.getDefault(), "%.1f", changePercent)}% maior. Não foi possível reduzir este PDF com uma regravação sem perda."
                        else -> if (english) "The file size did not change." else "O tamanho do ficheiro não se alterou."
                    }
                    ToolPanel(if (english) "Compression check" else "Verificação da compressão", tool.color) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(if (english) "Original" else "Original", color = MaxMuted, fontSize = 10.sp)
                                Text(android.text.format.Formatter.formatShortFileSize(context, originalBytes), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (english) "Result" else "Resultado", color = MaxMuted, fontSize = 10.sp)
                                Text(android.text.format.Formatter.formatShortFileSize(context, outputBytes), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        InfoLine(status, if (difference > 0L) Icons.Default.CheckCircle else Icons.Default.Info)
                        Text(
                            if (english) "PDF Max reports the actual file-size change; it does not promise that every PDF can be compressed further."
                            else "O PDF Max apresenta a alteração real do tamanho; nem todos os PDFs podem ser ainda mais comprimidos.",
                            color = MaxMuted, fontSize = 9.sp, lineHeight = 13.sp
                        )
                    }
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
                            if (resultSaved) {
                                IconButton(onClick = { DocumentActions.share(context, produced) }) {
                                    Icon(Icons.Default.Share, if (english) "Share result" else "Partilhar resultado", tint = tool.color)
                                }
                            } else {
                                Icon(
                                    Icons.Default.Schedule, null, tint = tool.color,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
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
                    val pendingToDiscard = resultFiles.toList()
                    scope.launch { discardPendingResults(pendingToDiscard) }
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
        ToolIdentityPanel(tool, english)
        Spacer(Modifier.height(13.dp))
        ToolStepIndicator(flowStage, tool.color, english)
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
            ToolPanel(toolSourceHeading(tool.title, english), tool.color) {
                if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaxCyan)
                OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (english) "Search your files" else "Pesquisar os seus ficheiros") }, colors = toolFieldColors(tool.color))
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

            ToolPanel(toolOptionsHeading(tool.title, english), tool.color) {
                when (tool.title) {
                    "Dividir PDF" -> {
                        NumberField(if (english) "First page" else "Primeira página", firstPage, tool.color) { firstPage = it }
                        NumberField(if (english) "Last page" else "Última página", lastPage, tool.color) { lastPage = it }
                    }
                    "Rodar páginas" -> {
                        Text(if (english) "Rotation" else "Rotação", color = MaxMuted, fontSize = 10.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(90, 180, 270).forEach { deg -> ChoiceChip(deg.toString() + "°", rotation == deg, { rotation = deg }, tool.color) }
                        }
                    }
                    "Proteger PDF" -> {
                        OutlinedTextField(password, { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(if (english) "PDF password (min. 8 chars)" else "Senha do PDF (mín. 8 caracteres)") }, visualTransformation = PasswordVisualTransformation(), colors = toolFieldColors(tool.color), singleLine = true)
                        InfoLine(
                            if (english) "Encrypted copy using AES-128 and an opening password. Use at least 8 characters. PDF permission flags are not a substitute for secure sharing."
                            else "Cópia encriptada com AES-128 e palavra-passe de abertura. Utilize pelo menos 8 caracteres. As restrições do formato PDF não substituem uma partilha segura.",
                            Icons.Default.Security
                        )
                    }
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
                        NumberField(if (english) "Insert after page (0 = first page)" else "Inserir depois da página (0 = primeira página)", pageAfter, tool.color) { pageAfter = it }
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
                            if (english) "Translate with Google using on-device ML Kit. The first use downloads the selected language models over Wi-Fi. Quality can vary by language." else "Traduzir com o Google através do ML Kit no dispositivo. Na primeira utilização, os modelos selecionados são descarregados por Wi-Fi. A qualidade pode variar conforme os idiomas.",
                            Icons.Default.Translate
                        )
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://translate.google.com")))
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp)
                        ) {
                            Text(if (english) "Google Translate" else "Google Tradutor", color = tool.color, fontSize = 10.sp)
                        }
                    }
                    "Achatar formulários" -> InfoLine(if (english) "Convert current AcroForm field appearances into page content. The fields will no longer be editable." else "Transforme a aparência atual dos campos AcroForm em conteúdo das páginas. Os campos deixam de ser editáveis.", Icons.Default.Layers)
                    "Remover metadados" -> InfoLine(if (english) "Create a copy without standard document information or the catalog XMP stream. Embedded object metadata may remain." else "Crie uma cópia sem as informações padrão do documento nem o fluxo XMP do catálogo. Metadados dentro de objetos incorporados podem permanecer.", Icons.Default.PrivacyTip)
                    "Relatório do PDF" -> InfoLine(if (english) "Inspect page count, file size, extracted words, lines and characters. Image-only scans may require OCR." else "Analise o número de páginas, tamanho, palavras, linhas e caracteres extraídos. Digitalizações só com imagem podem precisar de OCR.", Icons.Default.Analytics)
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
                Text(
                    when {
                        running -> if (english) "Processing…" else "A processar…"
                        tool.title == "Traduzir PDF" -> if (english) "Translate with Google" else "Traduzir com o Google"
                        else -> if (english) "Start " + localizedToolTitle(tool.title, true) else "Iniciar " + tool.title
                    },
                    color = Color.White, fontWeight = FontWeight.Bold
                )
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

private fun toolSourceHeading(title: String, english: Boolean): String = when (title) {
    "Juntar PDF" -> if (english) "1 · PDFS TO MERGE" else "1 · PDFs PARA JUNTAR"
    "Comparar PDFs" -> if (english) "1 · DOCUMENTS TO COMPARE" else "1 · DOCUMENTOS PARA COMPARAR"
    "Digitalizar e OCR", "Imagem para PDF" -> if (english) "1 · IMAGES & SCANS" else "1 · IMAGENS E DIGITALIZAÇÕES"
    "Criar ZIP" -> if (english) "1 · FILES TO ARCHIVE" else "1 · FICHEIROS PARA ARQUIVAR"
    "Detetar duplicados" -> if (english) "1 · LIBRARY TO ANALYSE" else "1 · BIBLIOTECA PARA ANALISAR"
    "Traduzir PDF" -> if (english) "1 · SOURCE DOCUMENT" else "1 · DOCUMENTO DE ORIGEM"
    else -> if (english) "1 · SOURCE DOCUMENT" else "1 · DOCUMENTO DE ORIGEM"
}

private fun toolOptionsHeading(title: String, english: Boolean): String = when (title) {
    "Editar PDF", "Adicionar texto", "Marca de água", "Numerar páginas" -> if (english) "2 · CONTENT & APPEARANCE" else "2 · CONTEÚDO E APARÊNCIA"
    "Converter PDF" -> if (english) "2 · EXPORT SETTINGS" else "2 · DEFINIÇÕES DE EXPORTAÇÃO"
    "Juntar PDF", "Dividir PDF", "Organizar páginas", "Extrair páginas", "Remover páginas", "Rodar páginas", "Adicionar página em branco" -> if (english) "2 · PAGE SETTINGS" else "2 · DEFINIÇÕES DE PÁGINA"
    "Proteger PDF", "Assinar PDF", "Preencher formulários", "Achatar formulários", "Remover metadados" -> if (english) "2 · SECURITY & PRIVACY" else "2 · SEGURANÇA E PRIVACIDADE"
    "Digitalizar e OCR", "Imagem para PDF", "PDF para imagem", "Extrair imagens" -> if (english) "2 · IMAGE PROCESSING" else "2 · PROCESSAMENTO DE IMAGEM"
    "Traduzir PDF" -> if (english) "2 · LANGUAGE SETTINGS" else "2 · DEFINIÇÕES DE IDIOMA"
    else -> if (english) "2 · OPERATION SETTINGS" else "2 · DEFINIÇÕES DA OPERAÇÃO"
}

@Composable
private fun ToolIdentityPanel(tool: ToolSpec, english: Boolean) {
    val (kicker, intro, tags) = when (tool.title) {
        "Editar PDF", "Adicionar texto", "Marca de água", "Numerar páginas" -> Triple(
            if (english) "DOCUMENT DESIGN STUDIO" else "ESTÚDIO DE DESIGN DOCUMENTAL",
            if (english) "Add clear, controlled visual changes to a new PDF copy." else "Adicione alterações visuais controladas a uma nova cópia do PDF.",
            listOf(if (english) "TEXT LAYERS" else "CAMADAS DE TEXTO", if (english) "PREVIEW RESULT" else "REVER RESULTADO")
        )
        "Juntar PDF", "Dividir PDF", "Organizar páginas", "Extrair páginas", "Remover páginas", "Rodar páginas", "Adicionar página em branco" -> Triple(
            if (english) "PAGE CONTROL ENGINE" else "MOTOR DE ORGANIZAÇÃO DE PÁGINAS",
            if (english) "Arrange document structure while keeping the source file untouched." else "Organize a estrutura do documento sem alterar o ficheiro original.",
            listOf(if (english) "PAGE WORKFLOW" else "FLUXO DE PÁGINAS", if (english) "NEW COPY" else "NOVA CÓPIA")
        )
        "Converter PDF" -> Triple(
            if (english) "FORMAT CONVERSION LAB" else "LABORATÓRIO DE CONVERSÃO",
            if (english) "Convert supported documents and review the exported file before saving." else "Converta documentos compatíveis e reveja o ficheiro exportado antes de guardar.",
            listOf(if (english) "OFFICE EXPORT" else "EXPORTAÇÃO OFFICE", if (english) "LOCAL OUTPUT" else "RESULTADO LOCAL")
        )
        "Proteger PDF", "Assinar PDF", "Preencher formulários", "Achatar formulários", "Remover metadados" -> Triple(
            if (english) "DOCUMENT SECURITY" else "SEGURANÇA DOCUMENTAL",
            if (english) "Apply a focused protection or privacy operation to a separate copy." else "Aplique uma operação específica de protecção ou privacidade numa cópia separada.",
            listOf(if (english) "SOURCE PRESERVED" else "ORIGINAL PRESERVADO", if (english) "REVIEW FIRST" else "REVER ANTES")
        )
        "Digitalizar e OCR", "Imagem para PDF", "PDF para imagem", "Extrair imagens" -> Triple(
            if (english) "IMAGE & OCR WORKSPACE" else "ESPAÇO DE IMAGEM E OCR",
            if (english) "Process scans and images into clean, reusable document output." else "Processe digitalizações e imagens para criar documentos limpos e reutilizáveis.",
            listOf(if (english) "IMAGE PIPELINE" else "FLUXO DE IMAGEM", if (english) "ON DEVICE" else "NO DISPOSITIVO")
        )
        "Traduzir PDF" -> Triple(
            if (english) "LANGUAGE WORKSPACE" else "ESPAÇO DE TRADUÇÃO",
            if (english) "Translate selectable document text with downloadable language models." else "Traduza o texto seleccionável com modelos de idioma descarregáveis.",
            listOf(if (english) "LANGUAGE MODELS" else "MODELOS DE IDIOMA", if (english) "REVIEW TEXT" else "REVER TEXTO")
        )
        "Comprimir PDF", "Reparar PDF" -> Triple(
            if (english) "PDF OPTIMIZATION ENGINE" else "MOTOR DE OPTIMIZAÇÃO PDF",
            if (english) "Rebuild the PDF and inspect the actual output size and integrity." else "Regrave o PDF e verifique o tamanho e o resultado real.",
            listOf(if (english) "FILE ANALYSIS" else "ANÁLISE DO FICHEIRO", if (english) "NO GUARANTEES" else "RESULTADO VERIFICADO")
        )
        else -> Triple(
            if (english) "DOCUMENT INTELLIGENCE" else "INTELIGÊNCIA DOCUMENTAL",
            if (english) "Run a focused operation on selected files and inspect its result." else "Execute uma operação específica nos ficheiros seleccionados e inspeccione o resultado.",
            listOf(if (english) "LOCAL WORKSPACE" else "ESPAÇO LOCAL", if (english) "CONTROLLED FLOW" else "FLUXO CONTROLADO")
        )
    }
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(tool.color.copy(alpha = 0.28f), Color(0xFF10172F), Color(0xFF09182D))))
            .border(1.dp, tool.color.copy(alpha = 0.48f), shape)
            .padding(15.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(66.dp).clip(RoundedCornerShape(19.dp))
                    .background(Brush.linearGradient(listOf(tool.color, tool.color.copy(alpha = 0.38f))))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(tool.icon, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(kicker, color = tool.color, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.25.sp)
                Spacer(Modifier.height(4.dp))
                Text(localizedToolTitle(tool.title, english), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp)
                Text(localizedToolSubtitle(tool.title, english), color = Color(0xFFB8C9E5), fontSize = 10.sp, lineHeight = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(13.dp))
        Text(intro, color = Color(0xFFD1DDF1), fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            tags.forEach { tag ->
                Box(Modifier.clip(CircleShape).background(tool.color.copy(alpha = 0.16f)).border(1.dp, tool.color.copy(alpha = 0.34f), CircleShape).padding(horizontal = 9.dp, vertical = 6.dp)) {
                    Text(tag, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.AutoAwesome, null, tint = tool.color, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ToolStepIndicator(stage: ToolFlowStage, accent: Color, english: Boolean) {
    val labels = if (english) listOf("01  SELECT", "02  PROCESS", "03  RESULT") else listOf("01  SELECÇÃO", "02  PROCESSAMENTO", "03  RESULTADO")
    val active = when (stage) {
        ToolFlowStage.SELECT -> 0
        ToolFlowStage.PROCESSING -> 1
        ToolFlowStage.RESULT -> 2
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == active
            val completed = index < active
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (selected || completed) accent.copy(alpha = if (selected) 0.22f else 0.11f) else Color(0xFF101F37))
                    .border(1.dp, if (selected) accent.copy(alpha = 0.78f) else Color(0xFF263B59), RoundedCornerShape(9.dp))
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (selected || completed) Color.White else MaxMuted, fontSize = 7.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ToolPanel(title: String, accent: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.13f), Color(0xFF0A1A31)))).border(1.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(16.dp)).padding(13.dp)) {
        Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Spacer(Modifier.height(9.dp))
        content()
    }
}

@Composable
private fun NumberField(label: String, value: String, accent: Color = MaxBlue, change: (String) -> Unit) {
    OutlinedTextField(value, change, modifier = Modifier.fillMaxWidth(), label = { Text(label) }, singleLine = true, colors = toolFieldColors(accent))
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
private fun toolFieldColors(accent: Color = MaxBlue) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Color.White, unfocusedTextColor = Color.White,
    focusedBorderColor = accent, unfocusedBorderColor = accent.copy(alpha = 0.48f),
    focusedLabelColor = accent, unfocusedLabelColor = MaxMuted,
    cursorColor = accent, focusedContainerColor = Color(0xFF0D1D35),
    unfocusedContainerColor = Color(0xFF0D1D35)
)
