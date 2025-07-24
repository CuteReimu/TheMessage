package com.fengsheng

import org.junit.Assert.assertEquals
import org.junit.Test

class Test {
    @Test
    fun sortTitlesTest() {
        val s = Statistics.sortTitles("💠👑🏅👑🏅💍💠🏅💠")
        assertEquals("👑👑💠💠💠💍🏅🏅🏅", s)
    }
}
