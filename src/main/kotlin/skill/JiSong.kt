package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.skill_ji_song_toc
import com.fengsheng.protos.Role.skill_ji_song_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 鬼脚技能【急送】：争夺阶段限一次，你可以弃置两张手牌，或从你的情报区弃置一张非黑色情报，然后将待收情报移至一名角色面前。
 */
class JiSong : ActiveSkill {
    override val skillId = SkillId.JI_SONG

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean =
        (r.cards.size >= 2 || r.messageCards.any { !it.isBlack() }) && r.getSkillUseCount(skillId) == 0

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null || r !== fsm.whoseFightTurn) {
            logger.error("现在不是发动[急送]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[急送]的时机")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[急送]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[急送]一回合只能发动一次")
            return
        }
        val pb = message as skill_ji_song_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        if (target === fsm.inFrontOfWhom) {
            logger.error("情报本来就在他面前")
            (r as? HumanPlayer)?.sendErrorMessage("情报本来就在他面前")
            return
        }
        val messageCard: Card?
        val cards: List<Card>?
        if (pb.cardIdsCount == 0 && pb.messageCard != 0) {
            messageCard = r.findMessageCard(pb.messageCard)
            if (messageCard == null) {
                logger.error("没有这张牌")
                (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return
            } else if (messageCard.colors.contains(color.Black)) {
                logger.error("这张牌不是非黑色")
                (r as? HumanPlayer)?.sendErrorMessage("这张牌不是非黑色")
                return
            }
            cards = null
        } else if (pb.cardIdsCount == 2 && pb.messageCard == 0) {
            cards = List(2) {
                val card = r.findCard(pb.getCardIds(it))
                if (card == null) {
                    logger.error("没有这张牌")
                    (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return
                }
                card
            }
            messageCard = null
        } else {
            logger.error("发动技能支付的条件不正确")
            (r as? HumanPlayer)?.sendErrorMessage("发动技能支付的条件不正确")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        if (messageCard != null) {
            logger.info("${r}发动了[急送]，弃掉了面前的${messageCard}，将情报移至${target}面前")
            r.deleteMessageCard(messageCard.id)
        } else {
            logger.info("${r}发动了[急送]，选择弃掉两张手牌，将情报移至${target}面前")
            g.playerDiscardCard(r, cards!!)
            g.addEvent(DiscardCardEvent(fsm.whoseTurn, r))
        }
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_ji_song_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                if (messageCard != null) builder.messageCard = messageCard.toPbCard()
                p.send(builder.build())
            }
        }
        g.resolve(fsm.copy(inFrontOfWhom = target, whoseFightTurn = target))
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            player.getSkillUseCount(SkillId.JI_SONG) == 0 || return false
            player.game!!.players.anyoneWillWinOrDie(e) || return false
            var value = Int.MIN_VALUE
            var target = e.inFrontOfWhom
            for (p in player.game!!.sortedFrom(player.game!!.players, player.location)) {
                p.alive || continue
                val v = player.calculateMessageCardValue(e.whoseTurn, p, e.messageCard)
                if (v > value) {
                    value = v
                    target = p
                }
            }
            target !== e.inFrontOfWhom || return false
            var valueRemove = -value
            var messageCard: Card? = null
            for (card in player.messageCards.toList()) {
                !card.isBlack() || continue
                val v = player.calculateRemoveCardValue(e.whoseTurn, player, card)
                if (v > valueRemove) {
                    valueRemove = v
                    messageCard = card
                }
            }
            value + valueRemove > 0 || value > 20 || return false
            if (messageCard != null && value + valueRemove < value - 20)
                messageCard = null
            var cards = emptyList<Card>()
            if (messageCard == null) {
                cards = player.cards.sortCards(player.identity, true).take(2)
                cards.size == 2 || return false
            }
            GameExecutor.post(player.game!!, {
                val builder = skill_ji_song_tos.newBuilder()
                cards.forEach { card -> builder.addCardIds(card.id) }
                builder.messageCard = messageCard?.id ?: 0
                builder.targetPlayerId = player.getAlternativeLocation(target.location)
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}