package com.nexauren.pdfmax.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DocumentUtilityOperationsTest {
    @Test
    fun duplicateFinderUsesContentNotNames() {
        val directory = Files.createTempDirectory("pdfmax-duplicates").toFile()
        try {
            val first = File(directory, "contract-a.pdf").apply { writeText("same document bytes") }
            val second = File(directory, "renamed-copy.pdf").apply { writeText("same document bytes") }
            val different = File(directory, "another.pdf").apply { writeText("different document") }

            val groups = DocumentUtilityOperations.findDuplicateGroups(listOf(first, second, different))

            assertEquals(1, groups.size)
            assertEquals(setOf(first, second), groups.single().toSet())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun duplicateFinderDoesNotMatchDifferentContentWithSameLength() {
        val directory = Files.createTempDirectory("pdfmax-same-size").toFile()
        try {
            val first = File(directory, "one.txt").apply { writeText("AAAA") }
            val second = File(directory, "two.txt").apply { writeText("BBBB") }

            val groups = DocumentUtilityOperations.findDuplicateGroups(listOf(first, second))

            assertTrue(groups.isEmpty())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun duplicateReportExplainsNoFilesWereDeleted() {
        val report = DocumentUtilityOperations.duplicateReport(emptyList())

        assertTrue(report.contains("Não foram encontrados"))
        assertTrue(report.contains("conteúdo"))
    }
}
