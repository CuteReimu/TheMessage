package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnDiscardCard
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.skill_ji_song_toc
import com.fengsheng.protos.Role.skill_ji_song_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 鬼脚技能【急送】：争夺阶段限一次，你可以弃置两张手牌，或从你的情报区弃置一张非黑色情报，然后将待收情报移至一名角色面前。
 */
class JiSong : InitialSkill, ActiveSkill {
    override val skillId = SkillId.JI_SONG

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            log.error("现在不是发动[急送]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[急送]的时机")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[急送]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[急送]一回合只能发动一次")
            return
        }
        val pb = message as skill_ji_song_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        if (target === fsm.inFrontOfWhom) {
            log.error("情报本来就在他面前")
            (r as? HumanPlayer)?.sendErrorMessage("情报本来就在他面前")
            return
        }
        val messageCard: Card?
        val cards: Array<Card>?
        if (pb.cardIdsCount == 0 && pb.messageCard != 0) {
            messageCard = r.findMessageCard(pb.messageCard)
            if (messageCard == null) {
                log.error("没有这张牌")
                (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return
            } else if (messageCard.colors.contains(color.Black)) {
                log.error("这张牌不是非黑色")
                (r as? HumanPlayer)?.sendErrorMessage("这张牌不是非黑色")
                return
            }
            cards = null
        } else if (pb.cardIdsCount == 2 && pb.messageCard == 0) {
            cards = Array(2) {
                val card = r.findCard(pb.getCardIds(it))
                if (card == null) {
                    log.error("没有这张牌")
                    (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return
                }
                card
            }
            messageCard = null
        } else {
            log.error("发动技能支付的条件不正确")
            (r as? HumanPlayer)?.sendErrorMessage("发动技能支付的条件不正确")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        if (messageCard != null) {
            log.info("${r}发动了[急送]，弃掉了面前的${messageCard}，将情报移至${target}面前")
            r.deleteMessageCard(messageCard.id)
        } else {
            log.info("${r}发动了[急送]，选择弃掉两张手牌，将情报移至${target}面前")
            g.playerDiscardCard(r, *cards!!)
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
        if (messageCard == null)
            g.resolve(fsm.copy(inFrontOfWhom = target, whoseFightTurn = target))
        else
            g.resolve(OnDiscardCard(fsm.whoseTurn, r, fsm.copy(inFrontOfWhom = target, whoseFightTurn = target)))
    }

    companion object {
        private val log = Logger.getLogger(JiSong::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.getSkillUseCount(SkillId.JI_SONG) > 0) return false
            if (player.cards.size < 2) return false
            val colors = e.messageCard.colors
            if (colors.size != 1) return false
            if (colors.first() == color.Black) {
                if (e.inFrontOfWhom !== player) return false
            } else {
                val identity = player.identity
                val identity2 = e.inFrontOfWhom.identity
                if (identity != color.Black && identity == identity2) return false
                if (identity2 == color.Black || colors[0] != identity2) return false
            }
            val players = player.game!!.players.filter { it !== e.inFrontOfWhom && it!!.alive }
            val target = players.randomOrNull() ?: return false
            val cards = Array(2) { player.cards[it] }
            GameExecutor.post(player.game!!, {
                val builder = skill_ji_song_tos.newBuilder()
                cards.forEach { card -> builder.addCardIds(card.id) }
                builder.targetPlayerId = player.getAlternativeLocation(target.location)
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}