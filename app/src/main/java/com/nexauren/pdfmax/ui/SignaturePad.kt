package com.nexauren.pdfmax.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SignaturePad(
    english: Boolean,
    onChanged: (List<List<Pair<Float, Float>>>) -> Unit
) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val latestStrokes by rememberUpdatedState(strokes)
    val latestSize by rememberUpdatedState(canvasSize)
    val latestCallback by rememberUpdatedState(onChanged)

    fun publish(value: List<List<Offset>>, size: IntSize) {
        if (size.width <= 0 || size.height <= 0) {
            latestCallback(emptyList())
        } else {
            latestCallback(value.map { stroke ->
                stroke.map { point ->
                    (point.x / size.width).coerceIn(0f, 1f) to
                        (point.y / size.height).coerceIn(0f, 1f)
                }
            })
        }
    }

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp))
            .background(Color(0xFF0D1D35)).border(1.dp, Color(0xFF31537A), RoundedCornerShape(15.dp))
            .padding(11.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (english) "Draw your signature" else "Desenhe a sua assinatura",
                color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = {
                strokes = emptyList()
                publish(emptyList(), canvasSize)
            }) {
                Text(if (english) "Clear" else "Limpar", color = Color(0xFF58B9FF), fontSize = 11.sp)
            }
        }
        Box(Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFFF8FAFF))) {
            Canvas(
                Modifier.fillMaxSize().onSizeChanged {
                    canvasSize = it
                    publish(strokes, it)
                }.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val next = latestStrokes + listOf(listOf(start))
                            strokes = next
                            publish(next, latestSize)
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val current = latestStrokes
                            if (current.isNotEmpty()) {
                                val updatedLast = current.last() + listOf(change.position)
                                val next = current.dropLast(1) + listOf(updatedLast)
                                strokes = next
                                publish(next, latestSize)
                            }
                        }
                    )
                }
            ) {
                strokes.forEach { stroke ->
                    if (stroke.size >= 2) {
                        val path = Path().apply {
                            moveTo(stroke.first().x, stroke.first().y)
                            stroke.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = Color(0xFF174A9E),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }
            if (strokes.isEmpty()) {
                Text(
                    if (english) "Sign here with your finger" else "Assine aqui com o dedo",
                    color = Color(0xFF9BA9BC), fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            if (english) "Visual signature stamp. It is not a certificate-based digital signature." else "Assinatura visual no documento. Não é uma assinatura digital com certificado.",
            color = Color(0xFF8EA2BF), fontSize = 9.sp, lineHeight = 13.sp
        )
    }
}
