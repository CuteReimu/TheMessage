package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common
import com.fengsheng.protos.Role.skill_jiang_ji_jiu_ji_toc
import org.apache.log4j.Logger

/**
 * 中年韩梅技能【将计就计】：你使用【误导】或者成为【误导】的目标之一时，可以翻回背面。
 */
class JiangJiJiuJi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.JIANG_JI_JIU_JI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        fsm.askWhom.alive || return null
        fsm.askWhom.findSkill(skillId) != null || return null
        fsm.cardType == Common.card_type.Wu_Dao || return null
        fsm.askWhom === fsm.player || fsm.askWhom === fsm.targetPlayer || fsm.askWhom === (fsm.currentFsm as? FightPhaseIdle)?.inFrontOfWhom || return null
        fsm.player.roleFaceUp || return null
        fsm.askWhom.addSkillUseCount(skillId)
        log.info("${fsm.askWhom}发动了[将计就计]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_jiang_ji_jiu_ji_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(fsm.askWhom.location)
                p.send(builder.build())
            }
        }
        g.playerSetRoleFaceUp(fsm.askWhom, false)
        return null
    }

    companion object {
        private val log = Logger.getLogger(JiangJiJiuJi::class.java)
    }
}