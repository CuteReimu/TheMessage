package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.card_type.Wei_Bi
import com.fengsheng.protos.Role.skill_cheng_fu_toc
import org.apache.log4j.Logger

/**
 * 李宁玉技能【城府】：【试探】和【威逼】对你无效。
 */
class ChengFu : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.CHENG_FU

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        fsm.cardType == Shi_Tan || fsm.cardType == Wei_Bi || return null
        fsm.targetPlayer === askWhom && askWhom.findSkill(skillId) != null || return null
        askWhom.roleFaceUp || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        askWhom.addSkillUseCount(skillId)
        log.info("${askWhom}触发了[城府]，${fsm.cardType}无效")
        for (player in g.players) {
            if (player is HumanPlayer) {
                val builder = skill_cheng_fu_toc.newBuilder()
                builder.playerId = player.getAlternativeLocation(askWhom.location)
                builder.fromPlayerId = player.getAlternativeLocation(fsm.player.location)
                fsm.card?.let { card ->
                    if (fsm.cardType != Shi_Tan || player === fsm.player)
                        builder.card = card.toPbCard()
                    else
                        builder.unknownCardCount = 1
                }
                builder.cardType = fsm.cardType
                player.send(builder.build())
            }
        }
        val oldResolveFunc = fsm.resolveFunc
        val newFsm = { valid: Boolean ->
            askWhom.resetSkillUseCount(skillId)
            oldResolveFunc(valid)
        }
        return ResolveResult(fsm.copy(resolveFunc = newFsm, valid = false), true)
    }

    companion object {
        private val log = Logger.getLogger(ChengFu::class.java)
    }
}