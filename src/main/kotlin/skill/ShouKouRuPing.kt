package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Role.skill_shou_kou_ru_ping_toc
import org.apache.log4j.Logger

/**
 * 哑巴技能【守口如瓶】：你对其他角色使用、其他角色对你使用【试探】和【威逼】时，取消原效果，改为双方各摸一张牌。
 */
class ShouKouRuPing : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.SHOU_KOU_RU_PING

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm
        if (fsm is OnUseCard) {
            fsm.askWhom.alive || return null
            fsm.askWhom.findSkill(skillId) != null || return null
            fsm.cardType == Shi_Tan || fsm.cardType == Wei_Bi || return null
            val targetPlayer = fsm.targetPlayer!!
            fsm.askWhom === fsm.player || fsm.askWhom === targetPlayer || return null
            fsm.askWhom.getSkillUseCount(skillId) == 0 || return null
            fsm.askWhom.addSkillUseCount(skillId)
            val r = fsm.askWhom
            log.info("${r}发动了[守口如瓶]")
            return ResolveResult(fsm.copy(resolveFunc = { valid: Boolean ->
                r.resetSkillUseCount(skillId)
                if (valid) {
                    for (p in fsm.askWhom.game!!.players) {
                        if (p is HumanPlayer) {
                            val builder = skill_shou_kou_ru_ping_toc.newBuilder()
                            builder.playerId = p.getAlternativeLocation(fsm.askWhom.location)
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
                    fsm.player.draw(1)
                    targetPlayer.draw(1)
                }
                OnFinishResolveCard(
                    fsm.player, fsm.targetPlayer, fsm.card?.getOriginCard(), fsm.cardType, MainPhaseIdle(fsm.whoseTurn),
                    discardAfterResolve = fsm.cardType != Shi_Tan
                )
            }), true)
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(ShouKouRuPing::class.java)
    }
}