package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnSendCardSkill
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Role.skill_jie_che_yun_huo_toc

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
        for (p in g.players) {
            p!!.skills += CannotPlayCard(listOf(Wu_Dao))
            if (p is HumanPlayer) {
                val builder = skill_jie_che_yun_huo_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        return null
    }
}