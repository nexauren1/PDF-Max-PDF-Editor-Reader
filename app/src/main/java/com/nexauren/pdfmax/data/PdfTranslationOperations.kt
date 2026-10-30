package com.nexauren.pdfmax.data

import android.content.Context
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.io.File

/** Local ML Kit translation. The document text is processed on-device after the language models download. */
object PdfTranslationOperations {
    fun translatePdf(
        context: Context,
        input: File,
        sourceTag: String,
        targetTag: String
    ): File {
        val source = TranslateLanguage.fromLanguageTag(sourceTag)
            ?: error("Idioma de origem não suportado.")
        val target = TranslateLanguage.fromLanguageTag(targetTag)
            ?: error("Idioma de destino não suportado.")
        require(source != target) { "Escolha idiomas diferentes para a origem e o destino." }

        val text = PdfOperations.extractText(context, input)
        require(text.isNotBlank()) {
            "Não foi encontrado texto selecionável. Se for uma digitalização, execute OCR primeiro."
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(source)
            .setTargetLanguage(target)
            .build()
        val translator = Translation.getClient(options)
        try {
            val conditions = DownloadConditions.Builder().requireWifi().build()
            Tasks.await(translator.downloadModelIfNeeded(conditions))
            val translated = splitText(text, 2400).map { chunk ->
                Tasks.await(translator.translate(chunk))
            }.joinToString("\n")
            require(translated.isNotBlank()) { "A tradução não devolveu texto." }
            val languageName = targetTag.uppercase()
            val outputTitle = input.nameWithoutExtension + "_translated_" + languageName
            return PdfOperations.createPdfFromText(context, outputTitle, translated)
        } finally {
            translator.close()
        }
    }

    private fun splitText(text: String, maxCharacters: Int): List<String> {
        val result = ArrayList<String>()
        var current = StringBuilder()
        text.replace("\r", "").split("\n").forEach { line ->
            val pieces = line.split(Regex("\\s+")).filter { it.isNotBlank() }
            for (word in pieces) {
                if (word.length > maxCharacters) {
                    if (current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder()
                    }
                    word.chunked(maxCharacters).forEach { result.add(it) }
                } else {
                    val extra = if (current.isEmpty()) word.length else word.length + 1
                    if (current.length + extra > maxCharacters && current.isNotEmpty()) {
                        result.add(current.toString())
                        current = StringBuilder(word)
                    } else {
                        if (current.isNotEmpty()) current.append(' ')
                        current.append(word)
                    }
                }
            }
            if (current.isNotEmpty()) {
                result.add(current.toString())
                current = StringBuilder()
            }
        }
        if (current.isNotEmpty()) result.add(current.toString())
        return result.filter { it.isNotBlank() }.ifEmpty { listOf(text.take(maxCharacters)) }
    }
}
