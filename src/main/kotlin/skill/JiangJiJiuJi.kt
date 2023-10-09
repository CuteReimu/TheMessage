package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Role.skill_jiang_ji_jiu_ji_toc
import org.apache.log4j.Logger

/**
 * 成年韩梅技能【将计就计】：你使用【误导】或者成为【误导】的目标之一时，可以翻回背面。
 */
class JiangJiJiuJi : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.JIANG_JI_JIU_JI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        askWhom.alive || return null
        fsm.cardType == Wu_Dao || return null
        askWhom === fsm.player || askWhom === fsm.targetPlayer || askWhom === (fsm.currentFsm as? FightPhaseIdle)?.inFrontOfWhom || return null
        askWhom.roleFaceUp || return null
        askWhom.addSkillUseCount(skillId)
        log.info("${askWhom}发动了[将计就计]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_jiang_ji_jiu_ji_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        g.playerSetRoleFaceUp(askWhom, false)
        return null
    }

    companion object {
        private val log = Logger.getLogger(JiangJiJiuJi::class.java)
    }
}