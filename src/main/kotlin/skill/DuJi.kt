package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 白昆山技能【毒计】：争夺阶段，你可以翻开此角色牌，然后指定两名其他角色，令他们相互抽取对方的一张手牌并展示之，你将展示的牌加入你的手牌，若展示的是黑色牌，你可以改为令抽取者选择一项：
 *  * 将其置入自己的情报区
 *  * 将其置入对方的情报区
 */
class DuJi : ActiveSkill {
    override val skillId = SkillId.DU_JI

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[毒计]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[毒计]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[毒计]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[毒计]")
            return
        }
        val pb = message as skill_du_ji_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerIdsCount != 2) {
            logger.error("[毒计]必须选择两名角色为目标")
            (r as? HumanPlayer)?.sendErrorMessage("[毒计]必须选择两名角色为目标")
            return
        }
        val idx1 = pb.getTargetPlayerIds(0)
        val idx2 = pb.getTargetPlayerIds(1)
        if (idx1 < 0 || idx1 >= g.players.size || idx2 < 0 || idx2 >= g.players.size) {
            logger.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        if (idx1 == 0 || idx2 == 0) {
            logger.error("不能以自己为目标")
            (r as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
            return
        }
        val target1 = g.players[r.getAbstractLocation(idx1)]!!
        val target2 = g.players[r.getAbstractLocation(idx2)]!!
        if (!target1.alive || !target2.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        if (target1.cards.isEmpty() || target2.cards.isEmpty()) {
            logger.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        val cards1 = target1.cards.toTypedArray()
        val cards2 = target2.cards.toTypedArray()
        val card1 = cards1.random()
        val card2 = cards2.random()
        logger.info("${r}发动了[毒计]，抽取了${target1}的${card1}和${target2}的$card2")
        target1.deleteCard(card1.id)
        target2.deleteCard(card2.id)
        r.cards.add(card1)
        r.cards.add(card2)
        r.game!!.addEvent(GiveCardEvent(fsm.whoseTurn, target2, target1))
        r.game!!.addEvent(GiveCardEvent(fsm.whoseTurn, target1, target2))
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_du_ji_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.addTargetPlayerIds(p.getAlternativeLocation(target1.location))
                builder.addTargetPlayerIds(p.getAlternativeLocation(target2.location))
                builder.addCards(card1.toPbCard())
                builder.addCards(card2.toPbCard())
                p.send(builder.build())
            }
        }
        val twoPlayersAndCards = ArrayList<TwoPlayersAndCard>()
        if (card2.colors.contains(color.Black))
            twoPlayersAndCards.add(TwoPlayersAndCard(target2, target1, card2))
        else
            r.game!!.addEvent(GiveCardEvent(fsm.whoseTurn, target1, r))
        if (card1.colors.contains(color.Black))
            twoPlayersAndCards.add(TwoPlayersAndCard(target1, target2, card1))
        else
            r.game!!.addEvent(GiveCardEvent(fsm.whoseTurn, target2, r))
        g.resolve(executeDuJiA(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), fsm.whoseTurn, r, twoPlayersAndCards))
    }

    private data class executeDuJiA(
        val fsm: Fsm,
        val whoseTurn: Player,
        val r: Player,
        val playerAndCards: ArrayList<TwoPlayersAndCard>,
        val asMessage: Boolean = false
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (playerAndCards.isEmpty()) {
                if (asMessage) r.game!!.addEvent(AddMessageCardEvent(whoseTurn))
                return ResolveResult(fsm, true)
            }
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_du_ji_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    for (twoPlayersAndCard in playerAndCards) {
                        builder.addTargetPlayerIds(p.getAlternativeLocation(twoPlayersAndCard.waitingPlayer.location))
                        builder.addCardIds(twoPlayersAndCard.card.id)
                    }
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq2 = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_du_ji_b_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    if (playerAndCards.isNotEmpty()) {
                        val builder = skill_du_ji_b_tos.newBuilder()
                        builder.enable = true
                        builder.cardId = playerAndCards[0].card.id
                        g.tryContinueResolveProtocol(r, builder.build())
                    } else {
                        g.tryContinueResolveProtocol(r, skill_du_ji_b_tos.newBuilder().setEnable(false).build())
                    }
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_du_ji_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_du_ji_b_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                if (asMessage) r.game!!.addEvent(AddMessageCardEvent(whoseTurn))
                for (playerAndCard in playerAndCards)
                    r.game!!.addEvent(GiveCardEvent(whoseTurn, playerAndCard.waitingPlayer, r))
                return ResolveResult(fsm, true)
            }
            val index = playerAndCards.indexOfFirst { it.card.id == message.cardId }
            if (index < 0) {
                logger.error("目标卡牌不存在")
                (player as? HumanPlayer)?.sendErrorMessage("目标卡牌不存在")
                return null
            }
            val selection = playerAndCards.removeAt(index)
            r.incrSeq()
            return ResolveResult(executeDuJiB(copy(asMessage = true), selection), true)
        }
    }

    private data class executeDuJiB(val fsm: executeDuJiA, val selection: TwoPlayersAndCard) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            logger.info("等待${selection.waitingPlayer}对${selection.card}进行选择")
            val g = selection.waitingPlayer.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_du_ji_b_toc.newBuilder()
                    builder.enable = true
                    builder.playerId = p.getAlternativeLocation(fsm.r.location)
                    builder.waitingPlayerId = p.getAlternativeLocation(selection.waitingPlayer.location)
                    builder.targetPlayerId = p.getAlternativeLocation(selection.fromPlayer.location)
                    builder.card = selection.card.toPbCard()
                    builder.waitingSecond = Config.WaitSecond
                    if (p === selection.waitingPlayer) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_du_ji_c_tos.newBuilder()
                                builder2.inFrontOfMe = false
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(selection.waitingPlayer, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (selection.waitingPlayer is RobotPlayer) {
                GameExecutor.post(g, {
                    g.tryContinueResolveProtocol(
                        selection.waitingPlayer,
                        skill_du_ji_c_tos.newBuilder().setInFrontOfMe(false).build()
                    )
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            val r = selection.waitingPlayer
            if (player !== r) {
                logger.error("当前没有轮到你结算[毒计]")
                (player as? HumanPlayer)?.sendErrorMessage("当前没有轮到你结算[毒计]")
                return null
            }
            if (message !is skill_du_ji_c_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            r.incrSeq()
            val target = if (message.inFrontOfMe) selection.waitingPlayer else selection.fromPlayer
            val card = selection.card
            logger.info("${r}选择将${card}放在${target}面前")
            fsm.r.deleteCard(card.id)
            target.messageCards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_du_ji_c_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(fsm.r.location)
                    builder.waitingPlayerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    private data class TwoPlayersAndCard(val fromPlayer: Player, val waitingPlayer: Player, val card: Card)

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.roleFaceUp) return false
            val players = ArrayList<Player>()
            for (p in player.game!!.players) {
                if (p !== player && p!!.alive && p.cards.isNotEmpty()) players.add(p)
            }
            val playerCount = players.size
            if (playerCount < 2) return false
            if (Random.nextInt(playerCount * playerCount) != 0) return false
            val i = Random.nextInt(playerCount)
            var j = Random.nextInt(playerCount)
            j = if (i == j) (j + 1) % playerCount else j
            val player1 = players[i]
            val player2 = players[j]
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(
                    player.game!!, player, skill_du_ji_a_tos.newBuilder()
                        .addTargetPlayerIds(player.getAlternativeLocation(player1.location))
                        .addTargetPlayerIds(player.getAlternativeLocation(player2.location)).build()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}