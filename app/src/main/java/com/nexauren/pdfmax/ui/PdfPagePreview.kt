package com.nexauren.pdfmax.ui

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PdfPagePreview(uri: Uri?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(uri) { mutableStateOf(false) }

    LaunchedEffect(uri) {
        bitmap = null
        failed = false
        if (uri != null) {
            bitmap = withContext(Dispatchers.IO) {
                try {
                    val descriptor = if (uri.scheme == "file") {
                        uri.path?.let { ParcelFileDescriptor.open(File(it), ParcelFileDescriptor.MODE_READ_ONLY) }
                    } else {
                        context.contentResolver.openFileDescriptor(uri, "r")
                    } ?: return@withContext null
                    descriptor.use { pfd ->
                        val renderer = PdfRenderer(pfd)
                        try {
                            if (renderer.pageCount == 0) return@withContext null
                            val page = renderer.openPage(0)
                            try {
                                val width = (page.width * 1.5f).toInt().coerceAtLeast(1)
                                val height = (page.height * 1.5f).toInt().coerceAtLeast(1)
                                val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                                page.render(output, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                output
                            } finally { page.close() }
                        } finally { renderer.close() }
                    }
                } catch (_: Exception) {
                    failed = true
                    null
                }
            }
        }
    }

    Box(
        modifier = modifier.fillMaxWidth()
            .background(Color(0xFF09162A), RoundedCornerShape(18.dp)).padding(10.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = "Página do documento PDF",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            uri == null -> SampleDocumentPage()
            failed -> Text("Não foi possível pré-visualizar este PDF.", color = Color.White)
            else -> Text("A preparar pré-visualização…", color = Color(0xFF9AAAC4))
        }
    }
}

@Composable
private fun SampleDocumentPage() {
    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFFF8FAFF), RoundedCornerShape(4.dp))
            .padding(horizontal = 22.dp, vertical = 26.dp)
    ) {
        Text("Business Report", color = Color(0xFF167BDF), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("2025  •  Strategic Analysis", color = Color(0xFF52627A), fontSize = 10.sp)
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().height(145.dp).background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF174A86), Color(0xFF21B8D5), Color(0xFF142746))
                )
            )
        )
        Spacer(Modifier.height(18.dp))
        repeat(7) { index ->
            Box(
                Modifier.fillMaxWidth(if (index % 3 == 0) 0.78f else 1f)
                    .height(5.dp).background(Color(0xFFD8E0EC), RoundedCornerShape(4.dp))
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text("PDF Max preview", color = Color(0xFF243B5B), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}
