package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.SendCardEvent
import com.fengsheng.protos.Common.card_type.Wu_Dao

/**
 * 火车司机技能【借车运货】：你传出的情报不能被误导。
 */
class JieCheYunHuo : TriggeredSkill {
    override val skillId = SkillId.JIE_CHE_YUN_HUO

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<SendCardEvent>(this) { event ->
            askWhom === event.sender
        } ?: return null
        g.players.forEach { it!!.skills += CannotPlayCard(listOf(Wu_Dao)) }
        return null
    }
}