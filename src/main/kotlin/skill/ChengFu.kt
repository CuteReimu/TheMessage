package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Role.skill_cheng_fu_toc
import org.apache.log4j.Logger

/**
 * 李宁玉技能【城府】：【试探】和【威逼】对你无效。
 */
class ChengFu : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.CHENG_FU

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        val r = fsm.askWhom
        if (fsm.targetPlayer !== r || r.findSkill(skillId) == null)
            return null
        r.roleFaceUp || return null
        r.getSkillUseCount(skillId) == 0 || return null
        r.addSkillUseCount(skillId)
        log.info("${r}触发了[城府]，${fsm.cardType}无效")
        for (player in g.players) {
            if (player is HumanPlayer) {
                val builder = skill_cheng_fu_toc.newBuilder()
                builder.playerId = player.getAlternativeLocation(r.location)
                builder.fromPlayerId = player.getAlternativeLocation(fsm.player.location)
                fsm.card?.let { card ->
                    if (fsm.cardType != Shi_Tan || player == fsm.player || player == r)
                        builder.card = card.toPbCard()
                    else
                        builder.unknownCardCount = 1
                }
                player.send(builder.build())
            }
        }
        val oldResolveFunc = fsm.resolveFunc
        val newFsm = { valid: Boolean ->
            r.resetSkillUseCount(skillId)
            oldResolveFunc(valid)
        }
        return ResolveResult(fsm.copy(resolveFunc = newFsm, valid = false), true)
    }

    companion object {
        private val log = Logger.getLogger(ChengFu::class.java)
    }
}