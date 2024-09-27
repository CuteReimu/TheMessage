package com.fengsheng

import org.junit.Assert.assertEquals
import org.junit.Test

class Test {
    @Test
    fun test() {
        val s = Statistics.sortTitles("💎👑🏅👑🏅💍💎🏅💎")
        assertEquals("👑👑💎💎💎💍🏅🏅🏅", s)
    }
}
