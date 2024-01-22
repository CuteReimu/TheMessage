package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_pin_ming_san_lang_toc
import com.fengsheng.protos.Role.skill_pin_ming_san_lang_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 秦无命技能【拼命三郎】：出牌阶段限一次，你可以将一张纯黑色手牌置入自己的情报区，然后摸三张牌。
 */
class PinMingSanLang : MainPhaseSkill() {
    override val skillId = SkillId.PIN_MING_SAN_LANG

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && r.cards.any { it.isPureBlack() } && r.messageCards.count(Black) < 2

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            log.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[拼命三郎]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[拼命三郎]一回合只能发动一次")
            return
        }
        val pb = message as skill_pin_ming_san_lang_tos
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
        if (!card.isPureBlack()) {
            log.error("这张牌不是纯黑色")
            (r as? HumanPlayer)?.sendErrorMessage("这张牌不是纯黑色")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info("${r}发动了[拼命三郎]，将手牌中的${card}置入自己的情报区")
        r.deleteCard(card.id)
        r.messageCards.add(card)
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_pin_ming_san_lang_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.card = card.toPbCard()
                p.send(builder.build())
            }
        }
        r.draw(3)
        g.addEvent(AddMessageCardEvent(r))
        g.continueResolve()
    }

    companion object {
        private val log = Logger.getLogger(PinMingSanLang::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.whoseTurn.getSkillUseCount(SkillId.PIN_MING_SAN_LANG) == 0 || return false
            e.whoseTurn.messageCards.count(Black) < 2 || return false
            val card = e.whoseTurn.cards.filter { it.isPureBlack() }.randomOrNull() ?: return false
            val cardId = card.id
            GameExecutor.post(e.whoseTurn.game!!, {
                val builder = skill_pin_ming_san_lang_tos.newBuilder()
                builder.cardId = cardId
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}