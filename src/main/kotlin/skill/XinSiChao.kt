package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.skill_xin_si_chao_toc
import com.fengsheng.protos.Role.skill_xin_si_chao_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 端木静技能【新思潮】：出牌阶段限一次，你可以弃置一张手牌，然后摸两张牌。
 */
class XinSiChao : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.XIN_SI_CHAO

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
            log.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[新思潮]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[新思潮]一回合只能发动一次")
            return
        }
        val pb = message as skill_xin_si_chao_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张卡")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张卡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info(r.toString() + "发动了[新思潮]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_xin_si_chao_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                p.send(builder.build())
            }
        }
        g.playerDiscardCard(r, card)
        r.draw(2)
        g.continueResolve()
    }

    companion object {
        private val log = Logger.getLogger(XinSiChao::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.player.getSkillUseCount(SkillId.XIN_SI_CHAO) > 0) return false
            val card = e.player.cards.firstOrNull() ?: return false
            val cardId = card.id
            GameExecutor.post(e.player.game!!, {
                val builder = skill_xin_si_chao_tos.newBuilder()
                builder.cardId = cardId
                skill.executeProtocol(e.player.game!!, e.player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}