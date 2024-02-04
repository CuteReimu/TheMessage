package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_yi_ya_huan_ya_toc
import com.fengsheng.protos.Role.skill_yi_ya_huan_ya_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 王魁技能【以牙还牙】：你接收黑色情报后，可以将一张黑色手牌置入情报传出者或其相邻角色的情报区，然后摸一张牌。
 */
class YiYaHuanYa : TriggeredSkill {
    override val skillId = SkillId.YI_YA_HUAN_YA

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.inFrontOfWhom || return@findEvent false
            event.messageCard.isBlack() || return@findEvent false
            askWhom.cards.isNotEmpty()
        } ?: return null
        return ResolveResult(executeYiYaHuanYa(g.fsm!!, event), true)
    }

    private data class executeYiYaHuanYa(val fsm: Fsm, val event: ReceiveCardEvent) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.whoseTurn.game!!.players)
                p!!.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.inFrontOfWhom)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message is end_receive_phase_tos) {
                if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                    logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                    player.sendErrorMessage("操作太晚了")
                    return null
                }
                player.incrSeq()
                return ResolveResult(fsm, true)
            }
            if (message !is skill_yi_ya_huan_ya_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            if (!card.colors.contains(color.Black)) {
                logger.error("你只能选择黑色手牌")
                (player as? HumanPlayer)?.sendErrorMessage("你只能选择黑色手牌")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            if (target !== event.sender && target !== event.sender.getNextLeftAlivePlayer() && target !== event.sender.getNextRightAlivePlayer()) {
                logger.error("你只能选择情报传出者或者其左边或右边的角色作为目标：${message.targetPlayerId}")
                (player as? HumanPlayer)?.sendErrorMessage("你只能选择情报传出者或者其左边或右边的角色作为目标：${message.targetPlayerId}")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[以牙还牙]，将${card}置入${target}的情报区")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_yi_ya_huan_ya_toc.newBuilder()
                    builder.card = card.toPbCard()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            r.draw(1)
            g.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(fsm: Fsm): Boolean {
            if (fsm !is executeYiYaHuanYa) return false
            val player = fsm.event.inFrontOfWhom
            val target = fsm.event.sender
            var value = -1
            var playerAndCard: PlayerAndCard? = null
            for (c in player.cards.sortCards(player.identity, true)) {
                c.isBlack() || continue
                for (p in listOf(target, target.getNextLeftAlivePlayer(), target.getNextRightAlivePlayer())) {
                    val v = player.calculateMessageCardValue(fsm.event.whoseTurn, p, c)
                    if (v > value) {
                        value = v
                        playerAndCard = PlayerAndCard(p, c)
                    }
                }
            }
            playerAndCard ?: return false
            GameExecutor.post(player.game!!, {
                val builder = skill_yi_ya_huan_ya_tos.newBuilder()
                builder.cardId = playerAndCard.card.id
                builder.targetPlayerId = player.getAlternativeLocation(playerAndCard.player.location)
                player.game!!.tryContinueResolveProtocol(player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}