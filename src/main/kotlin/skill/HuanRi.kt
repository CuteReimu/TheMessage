package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Common.card_type.Po_Yi
import com.fengsheng.protos.Role.skill_huan_ri_toc
import org.apache.log4j.Logger

/**
 * 鄭文先技能【换日】：你使用【调包】或【破译】后，可以将你的角色牌翻至面朝下。
 */
class HuanRi : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.HUAN_RI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        askWhom === fsm.player || return null
        askWhom.alive || return null
        askWhom.findSkill(skillId) != null || return null
        fsm.cardType == Diao_Bao || fsm.cardType == Po_Yi || return null
        fsm.player.roleFaceUp || return null
        askWhom.addSkillUseCount(skillId)
        log.info("${askWhom}发动了[换日]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_huan_ri_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        g.playerSetRoleFaceUp(askWhom, false)
        return null
    }

    companion object {
        private val log = Logger.getLogger(HuanRi::class.java)
    }
}