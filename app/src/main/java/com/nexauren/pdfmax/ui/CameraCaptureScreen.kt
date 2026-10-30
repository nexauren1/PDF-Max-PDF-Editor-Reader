package com.nexauren.pdfmax.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexauren.pdfmax.data.PdfOperations
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun CameraCapturePanel(
    english: Boolean,
    onCaptured: (File) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var cameraGranted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
        if (!it) message = if (english) "Camera permission was not granted." else "A permissão da câmara não foi concedida."
    }

    LaunchedEffect(cameraGranted, previewView) {
        val view = previewView
        if (cameraGranted && view != null) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                runCatching {
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                        .setJpegQuality(95)
                        .build()
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                    cameraProvider = provider
                    imageCapture = capture
                }.onFailure {
                    message = (if (english) "Could not start camera: " else "Não foi possível iniciar a câmara: ") + (it.message ?: "")
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    DisposableEffect(lifecycleOwner) {
        onDispose {
            runCatching { cameraProvider?.unbindAll() }
            cameraProvider = null
            imageCapture = null
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF0D1B33)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF31C9F5))
            Spacer(Modifier.width(8.dp))
            Text(
                if (english) "Scan with PDF Max camera" else "Digitalizar com a câmara do PDF Max",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color(0xFF31C9F5), strokeWidth = 2.dp)
        }
        Spacer(Modifier.height(10.dp))
        if (!cameraGranted) {
            Box(
                Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(13.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF163A5E), Color(0xFF101F39)))),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF31C9F5), modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (english) "Allow camera access to scan paper documents." else "Permita o acesso à câmara para digitalizar documentos em papel.", color = Color.White, fontSize = 10.sp)
                    Spacer(Modifier.height(9.dp))
                    Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text(if (english) "Enable camera" else "Ativar câmara")
                    }
                }
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = this
                    }
                },
                modifier = Modifier.fillMaxWidth().height(235.dp).clip(RoundedCornerShape(13.dp))
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    val capture = imageCapture
                    if (capture == null || busy) return@Button
                    val directory = File(PdfOperations.outputDirectory(), "Scans").apply { mkdirs() }
                    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val output = File(directory, "PDFMax_Scan_$stamp.jpg")
                    val options = ImageCapture.OutputFileOptions.Builder(output).build()
                    busy = true
                    message = null
                    capture.takePicture(
                        options,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                busy = false
                                message = if (english) "Image saved. Run OCR to create a searchable PDF." else "Imagem guardada. Execute o OCR para criar um PDF pesquisável."
                                onCaptured(output)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                busy = false
                                message = (if (english) "Capture failed: " else "Falha na captura: ") + (exception.message ?: "")
                            }
                        }
                    )
                },
                enabled = imageCapture != null && !busy,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087FF1)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text(if (english) "Capture document" else "Capturar documento")
            }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF39D6A1), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(it, color = Color(0xFFB4C7E2), fontSize = 9.sp, lineHeight = 13.sp)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, null, tint = Color(0xFF87A8CF), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(if (english) "Saved locally in Documents / PDF Max / Scans" else "Guardado localmente em Documentos / PDF Max / Scans", color = Color(0xFF91A5C1), fontSize = 9.sp)
        }
    }
}
