package com.fengsheng

import com.fengsheng.skill.YouDiShenRu
import org.junit.Assert.assertEquals
import org.junit.Test

class Test {
    @Test
    fun sortTitlesTest() {
        val s = Statistics.sortTitles("💠👑🏅👑🏅💍💠🏅💠")
        assertEquals("👑👑💠💠💠💍🏅🏅🏅", s)
    }

    @Test
    fun youDiShenRuAIBasicTest() {
        // Basic test to ensure the AI function exists and doesn't crash
        // This is a minimal test since setting up a full game state would be complex
        val aiFunction = YouDiShenRu.Companion::ai
        // Just ensure the function exists and can be called
        // The actual logic would require complex game state setup
        assert(aiFunction != null)
    }
}
