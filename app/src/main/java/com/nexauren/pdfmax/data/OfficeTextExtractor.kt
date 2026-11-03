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
        "txt", "csv", "md", "log" -> runCatching { file.inputStream().bufferedReader().use { reader -> reader.readTextBounded(120_000) } }.getOrDefault("")
        "rtf" -> "RTF é indexado pelo PDF Max, mas a conversão local ainda não está disponível. Guarde o documento como DOCX ou TXT."
        "doc", "xls", "ppt" -> "Formato Office antigo (" + file.extension.uppercase() + "). O PDF Max consegue indexar e organizar este ficheiro, mas a pré-visualização deste formato binário ainda não está disponível. Guarde-o como DOCX, XLSX ou PPTX para o visualizar aqui."
        else -> "Pré-visualização não disponível para este formato."
    }

    private fun zipXml(file: File, entries: List<String>, tag: String): String = runCatching {
        ZipFile(file).use { zip ->
            val output = StringBuilder()
            val pattern = Regex("<$tag(?:\\s+[^>]*)?>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL))
            for (name in entries) {
                if (output.length >= 120_000) break
                val entry = zip.getEntry(name) ?: continue
                val xml = zip.getInputStream(entry).bufferedReader().use { it.readTextBounded(2_000_000) }
                for (match in pattern.findAll(xml)) {
                    if (output.length >= 120_000) break
                    val decoded = decode(match.groupValues[1])
                    if (decoded.isNotBlank()) {
                        if (output.isNotEmpty()) output.append(10.toChar())
                        output.append(decoded.take(120_000 - output.length))
                    }
                }
            }
            output.toString()
        }
    }.getOrElse { "Não foi possível ler o conteúdo deste documento: " + (it.message ?: "formato inválido") }

    private fun java.io.Reader.readTextBounded(limit: Int): String {
        val output = StringBuilder()
        val buffer = CharArray(8192)
        while (output.length < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - output.length))
            if (count < 0) break
            output.append(buffer, 0, count)
        }
        return output.toString()
    }

    private fun decode(value: String): String = value
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&apos;", "'")
        .replace(Regex("<[^>]+>"), "")
}
