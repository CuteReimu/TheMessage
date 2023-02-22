package com.fengsheng.skill

import com.fengsheng.Gameimport

com.fengsheng.GameExecutorimport com.fengsheng.HumanPlayerimport com.fengsheng.Playerimport com.fengsheng.card.*import com.fengsheng.phase.MainPhaseIdleimport

com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 端木静技能【新思潮】：出牌阶段限一次，你可以弃置一张手牌，然后摸两张牌。
 */
class XinSiChao : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.XIN_SI_CHAO
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[新思潮]一回合只能发动一次")
            return
        }
        val pb = message as Role.skill_xin_si_chao_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张卡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info(r.toString() + "发动了[新思潮]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_xin_si_chao_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location())).build()
            )
        }
        g.playerDiscardCard(r, card)
        r.draw(2)
        g.continueResolve()
    }

    companion object {
        private val log = Logger.getLogger(XinSiChao::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.player.getSkillUseCount(SkillId.XIN_SI_CHAO) > 0) return false
            var card: Card? = null
            for (c in e.player.cards.values) {
                card = c
                break
            }
            if (card == null) return false
            val cardId = card.id
            GameExecutor.Companion.post(e.player.game, Runnable {
                skill.executeProtocol(
                    e.player.game, e.player, Role.skill_xin_si_chao_tos.newBuilder().setCardId(cardId).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}