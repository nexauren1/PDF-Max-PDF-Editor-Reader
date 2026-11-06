package com.nexauren.pdfmax.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageRangeParserTest {
    @Test fun parsesRangesAndKeepsEnteredOrder() {
        assertEquals(listOf(0, 1, 2, 4, 7, 8, 9), PageRangeParser.parse("1-3, 5, 8-10", 12))
    }

    @Test fun removesDuplicatesWithoutReordering() {
        assertEquals(listOf(2, 3, 4), PageRangeParser.parse("3-5, 4", 8))
    }

    @Test fun rejectsOutOfBoundsPage() {
        assertThrows(IllegalArgumentException::class.java) { PageRangeParser.parse("1, 9", 8) }
    }

    @Test fun rejectsDescendingRange() {
        assertThrows(IllegalArgumentException::class.java) { PageRangeParser.parse("5-2", 8) }
    }

    @Test fun rejectsEmptySegments() {
        assertThrows(IllegalArgumentException::class.java) { PageRangeParser.parse("1,,3", 8) }
    }
}
