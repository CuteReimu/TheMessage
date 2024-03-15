package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.skill_xin_si_chao_tos
import com.fengsheng.protos.skillXinSiChaoToc
import com.fengsheng.protos.skillXinSiChaoTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 端木静技能【新思潮】：出牌阶段限一次，你可以弃置一张手牌，然后摸两张牌。
 */
class XinSiChao : MainPhaseSkill() {
    override val skillId = SkillId.XIN_SI_CHAO

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && r.cards.isNotEmpty()

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[新思潮]一回合只能发动一次")
            r.sendErrorMessage("[新思潮]一回合只能发动一次")
            return
        }
        val pb = message as skill_xin_si_chao_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张卡")
            r.sendErrorMessage("没有这张卡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[新思潮]")
        g.players.send { skillXinSiChaoToc { playerId = it.getAlternativeLocation(r.location) } }
        g.playerDiscardCard(r, card)
        r.draw(2)
        g.addEvent(DiscardCardEvent(r, r))
        g.continueResolve()
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.whoseTurn.getSkillUseCount(SkillId.XIN_SI_CHAO) == 0 || return false
            val card = e.whoseTurn.cards.ifEmpty { return false }
            val cardId = card.bestCard(e.whoseTurn.identity, true).id
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, skillXinSiChaoTos { this.cardId = cardId })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}