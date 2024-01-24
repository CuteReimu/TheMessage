package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_zi_zheng_qing_bai_toc
import com.fengsheng.protos.Role.skill_zi_zheng_qing_bai_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 陈安娜技能【自证清白】：出牌阶段限一次，你可以弃置一张与自己身份颜色不同的手牌，然后摸两张牌。（潜伏=红色，特工=蓝色，神秘人随意弃牌）
 */
class ZiZhengQingBai : MainPhaseSkill() {
    override val skillId = SkillId.ZI_ZHENG_QING_BAI

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && (
                r.identity == Black && r.cards.isNotEmpty() ||
                        r.identity != Black && r.cards.any { r.identity !in it.colors })

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[自证清白]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[自证清白]一回合只能发动一次")
            return
        }
        val pb = message as skill_zi_zheng_qing_bai_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张卡")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张卡")
            return
        }
        if (r.identity != Black && r.identity in card.colors) {
            logger.error("你不能弃置与自己身份相同颜色的牌")
            (r as? HumanPlayer)?.sendErrorMessage("你不能弃置与自己身份相同颜色的牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[自证清白]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_zi_zheng_qing_bai_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.addAllColors(card.colors)
                p.send(builder.build())
            }
        }
        g.playerDiscardCard(r, card)
        r.draw(2)
        g.addEvent(DiscardCardEvent(r, r))
        g.continueResolve()
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.whoseTurn.getSkillUseCount(SkillId.ZI_ZHENG_QING_BAI) > 0) return false
            val card = e.whoseTurn.cards.find {
                e.whoseTurn.identity == Black || e.whoseTurn.identity !in it.colors
            } ?: return false
            val cardId = card.id
            GameExecutor.post(e.whoseTurn.game!!, {
                val builder = skill_zi_zheng_qing_bai_tos.newBuilder()
                builder.cardId = cardId
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}