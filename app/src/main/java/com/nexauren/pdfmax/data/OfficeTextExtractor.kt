package com.nexauren.pdfmax.data

import java.io.File
import java.util.zip.ZipFile

object OfficeTextExtractor {
    fun extract(file: File): String = when (file.extension.lowercase()) {
        "docx" -> zipXml(file, listOf("word/document.xml"), "w:t")
        "xlsx" -> zipXml(file, listOf("xl/sharedStrings.xml", "xl/worksheets/sheet1.xml"), "t")
        "pptx" -> {
            val names = ZipFile(file).use { zip ->
                zip.entries().asSequence().map { it.name }
                    .filter { it.matches(Regex("ppt/slides/slide[0-9]+\\.xml")) }
                    .sortedBy { it.substringAfter("slide").substringBefore(".").toIntOrNull() ?: 0 }.toList()
            }
            zipXml(file, names, "a:t")
        }
        "txt", "csv", "md", "log", "rtf" -> runCatching { file.readText().take(120_000) }.getOrDefault("")
        "doc", "xls", "ppt" -> "Formato Office antigo (" + file.extension.uppercase() + "). O PDF Max consegue indexar e organizar este ficheiro, mas a pré-visualização deste formato binário ainda não está disponível. Guarde-o como DOCX, XLSX ou PPTX para o visualizar aqui."
        else -> "Pré-visualização não disponível para este formato."
    }

    private fun zipXml(file: File, entries: List<String>, tag: String): String = runCatching {
        ZipFile(file).use { zip ->
            entries.mapNotNull { name ->
                val entry = zip.getEntry(name) ?: return@mapNotNull null
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                Regex("<$tag(?:\\s+[^>]*)?>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL))
                    .findAll(xml).map { decode(it.groupValues[1]) }
                    .filter { it.isNotBlank() }.joinToString(" ")
            }.filter { it.isNotBlank() }.joinToString("\n\n").take(120_000)
        }
    }.getOrElse { "Não foi possível ler o conteúdo deste documento: " + (it.message ?: "formato inválido") }

    private fun decode(value: String): String = value
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("<[^>]+>"), "")
}
