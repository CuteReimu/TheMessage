package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnSendCardSkill
import com.fengsheng.protos.Common.card_type.Wu_Dao

/**
 * 火车司机技能【借车运货】：你传出的情报不能被误导。
 */
class JieCheYunHuo : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.JIE_CHE_YUN_HUO

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnSendCardSkill ?: return null
        askWhom === fsm.sender || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        askWhom.addSkillUseCount(skillId)
        g.players.forEach { it!!.skills += CannotPlayCard(listOf(Wu_Dao)) }
        return null
    }
}