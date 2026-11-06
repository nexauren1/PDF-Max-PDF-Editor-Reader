package com.nexauren.pdfmax.data

object PageRangeParser {
    /** Parses one-based expressions such as "1-3, 5, 8-10" into zero-based indexes. */
    fun parse(expression: String, pageCount: Int): List<Int> {
        require(pageCount > 0) { "The PDF has no pages" }
        require(expression.isNotBlank()) { "Enter at least one page number or range" }

        val selected = linkedSetOf<Int>()
        expression.split(',').forEach { raw ->
            val token = raw.trim()
            require(token.isNotEmpty()) { "Check the page range format" }

            if ('-' in token) {
                val parts = token.split('-')
                require(parts.size == 2) { "Invalid page range: " + token }
                val first = parts[0].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid page number: " + parts[0])
                val last = parts[1].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid page number: " + parts[1])
                require(first in 1..pageCount && last in 1..pageCount) {
                    "Page range " + token + " is outside 1-" + pageCount
                }
                require(first <= last) { "The start of range " + token + " must not exceed its end" }
                for (page in first..last) selected.add(page - 1)
            } else {
                val page = token.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid page number: " + token)
                require(page in 1..pageCount) { "Page " + page + " is outside 1-" + pageCount }
                selected.add(page - 1)
            }
        }
        require(selected.isNotEmpty()) { "No pages were selected" }
        return selected.toList()
    }
}
