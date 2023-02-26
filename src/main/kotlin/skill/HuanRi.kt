package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Role.skill_huan_ri_toc
import org.apache.log4j.Logger

/**
 * 鄭文先技能【换日】：你使用【调包】或【破译】后，可以将你的角色牌翻至面朝下。
 */
class HuanRi : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.HUAN_RI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard
        if (fsm == null || fsm.askWhom.findSkill(skillId) == null || !fsm.askWhom.alive) return null
        if (fsm.player !== fsm.askWhom) return null
        if (fsm.cardType != card_type.Diao_Bao && fsm.cardType != card_type.Po_Yi) return null
        if (!fsm.player.roleFaceUp) return null
        fsm.askWhom.addSkillUseCount(skillId)
        log.info("${fsm.askWhom}发动了[换日]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                skill_huan_ri_toc.newBuilder().setPlayerId(p.getAlternativeLocation(fsm.askWhom.location)).build()
            )
        }
        g.playerSetRoleFaceUp(fsm.askWhom, false)
        return null
    }

    companion object {
        private val log = Logger.getLogger(HuanRi::class.java)
    }
}