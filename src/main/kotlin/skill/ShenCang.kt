package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.skill_shen_cang_toc
import org.apache.log4j.Logger

/**
 * 盛老板技能【深藏】：你使用【威逼】、【风云变幻】或【截获】后，可以将此角色牌翻至面朝下。
 */
class ShenCang : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.SHEN_CANG

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        askWhom === fsm.player || return null
        askWhom.alive || return null
        askWhom.findSkill(skillId) != null || return null
        fsm.cardType == Wei_Bi || fsm.cardType == Feng_Yun_Bian_Huan || fsm.cardType == Jie_Huo || return null
        fsm.player.roleFaceUp || return null
        askWhom.addSkillUseCount(skillId)
        log.info("${askWhom}发动了[深藏]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_shen_cang_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        g.playerSetRoleFaceUp(askWhom, false)
        return null
    }

    companion object {
        private val log = Logger.getLogger(ShenCang::class.java)
    }
}