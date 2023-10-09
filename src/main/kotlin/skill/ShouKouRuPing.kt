package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Role.skill_shou_kou_ru_ping_toc
import org.apache.log4j.Logger

/**
 * 哑炮技能【守口如瓶】：你对其他角色使用、其他角色对你使用【试探】和【威逼】时，取消原效果，改为双方各摸一张牌。
 */
class ShouKouRuPing : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.SHOU_KOU_RU_PING

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm
        if (fsm is OnUseCard) {
            askWhom.alive || return null
            fsm.cardType == Shi_Tan || fsm.cardType == Wei_Bi || return null
            fsm.valid || return null
            val targetPlayer = fsm.targetPlayer!!
            askWhom === fsm.player || askWhom === targetPlayer || return null
            askWhom.getSkillUseCount(skillId) == 0 || return null
            askWhom.addSkillUseCount(skillId)
            log.info("${askWhom}发动了[守口如瓶]")
            for (p in askWhom.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_shou_kou_ru_ping_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(askWhom.location)
                    builder.cardPlayerId = p.getAlternativeLocation(fsm.player.location)
                    builder.cardTargetPlayerId = p.getAlternativeLocation(targetPlayer.location)
                    builder.cardType = fsm.cardType
                    fsm.card?.let { card ->
                        if (fsm.cardType != Shi_Tan || p === fsm.player)
                            builder.card = card.toPbCard()
                        else
                            builder.unknownCardCount = 1
                    }
                    p.send(builder.build())
                }
            }
            g.sortedFrom(listOf(fsm.player, targetPlayer), fsm.whoseTurn.location).forEach { it.draw(1) }
            val oldResolveFunc = fsm.resolveFunc
            return ResolveResult(fsm.copy(resolveFunc = { valid: Boolean ->
                askWhom.resetSkillUseCount(skillId)
                oldResolveFunc(valid)
            }, valid = false), true)
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(ShouKouRuPing::class.java)
    }
}