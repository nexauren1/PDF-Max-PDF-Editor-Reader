package com.nexauren.pdfmax

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nexauren.pdfmax.ui.PDFMaxApp
import com.nexauren.pdfmax.ui.theme.PDFMaxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incomingPdf = if (intent?.action == Intent.ACTION_VIEW) intent?.data else null
        setContent { PDFMaxTheme { PDFMaxApp(initialPdf = incomingPdf) } }
    }
}
