package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.count
import com.fengsheng.card.countTrueCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.card_type.Cheng_Qing
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Role.skill_pin_ming_san_lang_tos
import com.fengsheng.protos.skillPinMingSanLangToc
import com.fengsheng.protos.skillPinMingSanLangTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 秦无命技能【拼命三郎】：出牌阶段限一次，你可以将一张纯黑色手牌置入自己的情报区，然后摸三张牌。
 */
class PinMingSanLang : MainPhaseSkill() {
    override val skillId = SkillId.PIN_MING_SAN_LANG

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean =
        super.mainPhaseNeedNotify(r) && r.cards.any { it.isPureBlack() } && r.messageCards.count(Black) < 2

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[拼命三郎]一回合只能发动一次")
            r.sendErrorMessage("[拼命三郎]一回合只能发动一次")
            return
        }
        val pb = message as skill_pin_ming_san_lang_tos
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
        if (!card.isPureBlack()) {
            logger.error("这张牌不是纯黑色")
            r.sendErrorMessage("这张牌不是纯黑色")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[拼命三郎]，将手牌中的${card}置入自己的情报区")
        r.deleteCard(card.id)
        r.messageCards.add(card)
        g.players.send {
            skillPinMingSanLangToc {
                playerId = it.getAlternativeLocation(r.location)
                this.card = card.toPbCard()
            }
        }
        r.draw(3)
        g.addEvent(AddMessageCardEvent(r))
        g.continueResolve()
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val p = e.whoseTurn
            p.getSkillUseCount(SkillId.PIN_MING_SAN_LANG) == 0 || return false
            if (p.messageCards.count(Black) == 2) {
                if (p.identity == Black) {
                    when (p.secretTask) {
                        Killer -> if (p.messageCards.countTrueCard() < 2) return false
                        Pioneer -> if (p.messageCards.countTrueCard() < 1) return false
                        Sweeper -> if (p.messageCards.run { count(Red) > 1 || count(Blue) > 1 }) return false
                        else -> return false
                    }
                } else if (!p.cards.any { it.type == Cheng_Qing })
                    return false
                else
                    p.getSkillUseCount(SkillId.YU_SI_WANG_PO) > 0 || return false
            }
            val card = p.cards.filter { it.isPureBlack() }.ifEmpty { return false }.bestCard(p.identity, true)
            GameExecutor.post(p.game!!, {
                skill.executeProtocol(p.game!!, p, skillPinMingSanLangTos { cardId = card.id })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
