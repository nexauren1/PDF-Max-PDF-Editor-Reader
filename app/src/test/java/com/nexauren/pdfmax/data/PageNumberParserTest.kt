package com.nexauren.pdfmax.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PageNumberParserTest {
    @Test fun parsesCommaSeparatedPages() {
        assertEquals(listOf(1, 3, 12), PageNumberParser.parse("1, 3,12"))
    }

    @Test fun rejectsEmptyInput() {
        assertThrows(IllegalArgumentException::class.java) { PageNumberParser.parse(" ") }
    }

    @Test fun rejectsInvalidTokensInsteadOfSilentlyDroppingThem() {
        assertThrows(IllegalArgumentException::class.java) { PageNumberParser.parse("1,abc,4") }
    }

    @Test fun rejectsZeroAndNegativePages() {
        assertThrows(IllegalArgumentException::class.java) { PageNumberParser.parse("1,0,-2") }
    }
}
