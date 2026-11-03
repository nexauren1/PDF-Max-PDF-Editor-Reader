package com.nexauren.pdfmax.data

/** Strict parser for user-entered, 1-based PDF page lists. */
object PageNumberParser {
    fun parse(input: String): List<Int> {
        val tokens = input.split(",").map { it.trim() }
        require(tokens.isNotEmpty() && tokens.none { it.isEmpty() }) {
            "Indique páginas válidas separadas por vírgulas."
        }
        val pages = tokens.map { token ->
            token.toIntOrNull()
                ?: throw IllegalArgumentException("Página inválida: " + token)
        }
        require(pages.all { it > 0 }) { "Os números das páginas devem ser superiores a zero." }
        return pages
    }
}
