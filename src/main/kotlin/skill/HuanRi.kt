package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Common.card_type.Po_Yi
import com.fengsheng.protos.skillHuanRiToc
import org.apache.logging.log4j.kotlin.logger

/**
 * 鄭文先技能【换日】：你使用【调包】或【破译】后，可以将你的角色牌翻至面朝下。
 */
class HuanRi : TriggeredSkill {
    override val skillId = SkillId.HUAN_RI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        g.findEvent<FinishResolveCardEvent>(this) { event ->
            askWhom === event.player || return@findEvent false
            askWhom.alive || return@findEvent false
            event.cardType == Diao_Bao || event.cardType == Po_Yi || return@findEvent false
            event.player.roleFaceUp
        } ?: return null
        logger.info("${askWhom}发动了[换日]")
        g.players.send { skillHuanRiToc { playerId = it.getAlternativeLocation(askWhom.location) } }
        g.playerSetRoleFaceUp(askWhom, false)
        return null
    }
}
