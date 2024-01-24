package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.skill_shen_cang_toc
import org.apache.logging.log4j.kotlin.logger

/**
 * 盛老板技能【深藏】：你使用【威逼】、【风云变幻】或【截获】后，可以将此角色牌翻至面朝下。
 */
class ShenCang : TriggeredSkill {
    override val skillId = SkillId.SHEN_CANG

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<FinishResolveCardEvent>(this) { event ->
            askWhom === event.player || return@findEvent false
            askWhom.alive || return@findEvent false
            askWhom.roleFaceUp || return@findEvent false
            event.cardType == Wei_Bi || event.cardType == Feng_Yun_Bian_Huan || event.cardType == Jie_Huo
        } ?: return null
        logger.info("${askWhom}发动了[深藏]")
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
    }
}