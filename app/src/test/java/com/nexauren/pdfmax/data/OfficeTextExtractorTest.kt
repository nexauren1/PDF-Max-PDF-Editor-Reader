package com.nexauren.pdfmax.data

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeTextExtractorTest {
    @Test fun extractsTextFromDocxXml() {
        val file = File.createTempFile("pdfmax-office-", ".docx")
        try {
            ZipOutputStream(FileOutputStream(file)).use { zip ->
                zip.putNextEntry(ZipEntry("word/document.xml"))
                zip.write(
                    """<?xml version="1.0"?><w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Hello</w:t><w:t> PDF Max</w:t></w:r></w:p></w:body></w:document>"""
                        .toByteArray(Charsets.UTF_8)
                )
                zip.closeEntry()
            }
            assertEquals("Hello PDF Max", OfficeTextExtractor.extract(file))
        } finally {
            file.delete()
        }
    }

    @Test fun limitsPlainTextReadsTo120000Characters() {
        val file = File.createTempFile("pdfmax-text-", ".txt")
        try {
            file.writeText("A".repeat(150_000))
            assertEquals(120_000, OfficeTextExtractor.extract(file).length)
        } finally {
            file.delete()
        }
    }

    @Test fun identifiesRtfAsIndexedButNotLocallyConvertible() {
        val file = File.createTempFile("pdfmax-rtf-", ".rtf")
        try {
            file.writeText("{\\rtf1 test}")
            assertTrue(OfficeTextExtractor.extract(file).contains("RTF"))
            assertTrue(OfficeTextExtractor.extract(file).contains("não está disponível"))
        } finally {
            file.delete()
        }
    }
}
