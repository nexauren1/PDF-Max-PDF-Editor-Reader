package com.nexauren.pdfmax.data

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Offline utilities for the PDF Max internal document library. */
object DocumentUtilityOperations {
    fun extractTextToFile(context: Context, input: File): File {
        val text = PdfOperations.extractText(context, input)
        require(text.isNotBlank()) { "Não foi encontrado texto selecionável neste PDF. Para digitalizações, use primeiro o OCR." }
        val directory = PdfOperations.outputDirectory()
        val output = File(directory, input.nameWithoutExtension + "_texto_" + System.currentTimeMillis() + ".txt")
        output.writeText(text, Charsets.UTF_8)
        return output
    }

    fun createZip(files: List<File>): File {
        require(files.isNotEmpty()) { "Selecione pelo menos um ficheiro." }
        val directory = PdfOperations.outputDirectory()
        val output = File(directory, "PDF_Max_Arquivo_" + System.currentTimeMillis() + ".zip")
        val usedNames = mutableSetOf<String>()
        try {
            ZipOutputStream(FileOutputStream(output)).use { zip ->
                files.forEach { file ->
                    require(file.isFile && file.canRead()) { "Não foi possível ler " + file.name + "." }
                    var entryName = file.name.replace("/", "_").replace("\\", "_")
                    if (!usedNames.add(entryName)) {
                        entryName = file.nameWithoutExtension + "_" + usedNames.size + "." + file.extension
                        usedNames.add(entryName)
                    }
                    zip.putNextEntry(ZipEntry(entryName))
                    FileInputStream(file).use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } catch (error: Exception) {
            output.delete()
            throw error
        }
        return output
    }

    /**
     * Exact duplicate detection: files are first grouped by byte size and only
     * same-size candidates are SHA-256 hashed, keeping the scan efficient.
     */
    fun findDuplicateGroups(files: List<File>): List<List<File>> {
        return files.asSequence()
            .filter { it.isFile && it.canRead() }
            .groupBy { it.length() }
            .values.asSequence()
            .filter { it.size > 1 }
            .flatMap { sameSize ->
                sameSize.groupBy { sha256(it) }.values.asSequence().filter { it.size > 1 }
            }
            .map { it.sortedBy { file -> file.name.lowercase() } }
            .sortedByDescending { group -> group.sumOf { it.length() } }
            .toList()
    }

    fun duplicateReport(groups: List<List<File>>): String {
        if (groups.isEmpty()) {
            return "Não foram encontrados ficheiros duplicados. A verificação compara o conteúdo, não apenas os nomes."
        }
        return buildString {
            append("Grupos duplicados encontrados: ")
            append(groups.size)
            append("\n\n")
            groups.forEachIndexed { index, group ->
                val bytes = group.first().length()
                append("Grupo ")
                append(index + 1)
                append(" • ")
                append(formatBytes(bytes))
                append(" por cópia\n")
                group.forEach { file ->
                    append("• ")
                    append(file.name)
                    append("\n")
                    append("  ")
                    append(file.parent ?: "")
                    append("\n")
                }
                append("\n")
            }
            append("Os ficheiros não foram apagados. Reveja os grupos antes de eliminar qualquer cópia.")
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
        else -> bytes.toString() + " B"
    }
}
