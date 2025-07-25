package com.fengsheng

import com.fengsheng.skill.DuMing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Test {
    @Test
    fun sortTitlesTest() {
        val s = Statistics.sortTitles("💠👑🏅👑🏅💍💠🏅💠")
        assertEquals("👑👑💠💠💠💍🏅🏅🏅", s)
    }

    @Test
    fun jinZilaiDuMingSkillExistsTest() {
        // Simple test to verify DuMing skill class exists and can be instantiated
        val duMingSkill = DuMing()
        assertTrue("DuMing skill should exist", duMingSkill != null)
        assertTrue("DuMing should be a triggered skill", duMingSkill.isInitialSkill)
    }
}
