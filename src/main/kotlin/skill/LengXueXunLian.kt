package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.MiLing.executeMiLing
import com.fengsheng.card.count
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Common.card_type.Mi_Ling
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.direction.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * SP韩梅技能【冷血训练】：你需要传出情报时，可以改为展示牌堆顶的两张牌，从中选择一张（若有黑色牌则必须选择一张黑色牌）作为情报面朝上传出，并锁定一名角色，且令所有角色本回合中不能使用【调包】，之后将未选择的那张加入你的手牌。
 */
class LengXueXunLian : InitialSkill, ActiveSkill {
    override val skillId = SkillId.LENG_XUE_XUN_LIAN

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = false

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        message as skill_leng_xue_xun_lian_a_tos
        if (r is HumanPlayer && !r.checkSeq(message.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = g.fsm
        if (fsm is SendPhaseStart && r === fsm.whoseTurn) {
            g.resolve(executeLengXueXunLian(fsm.whoseTurn, fsm.whoseTurn, g.deck.draw(2)))
        } else if (fsm is executeMiLing && r === fsm.target) {
            g.resolve(
                OnFinishResolveCard(
                    fsm.sendPhase.whoseTurn,
                    fsm.sendPhase.whoseTurn,
                    fsm.target,
                    fsm.card,
                    Mi_Ling,
                    executeLengXueXunLian(fsm.sendPhase.whoseTurn, fsm.target, g.deck.draw(2)),
                    discardAfterResolve = false
                )
            )
        } else {
            log.error("现在不能发动[冷血训练]")
            (r as? HumanPlayer)?.sendErrorMessage("现在不能发动[冷血训练]")
        }
    }

    private data class executeLengXueXunLian(
        val whoseTurn: Player,
        val r: Player,
        val cards: Array<Card>
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            r.incrSeq()
            log.info("${r}发动了[冷血训练]，展示了牌堆顶的${cards.contentToString()}")
            r.skills += MustLockOne()
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_leng_xue_xun_lian_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    cards.forEach { builder.addCards(it.toPbCard()) }
                    builder.waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq = p.seq
                        builder.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_leng_xue_xun_lian_b_tos.newBuilder()
                                cards.sortBy { !it.isBlack() }
                                val card = cards.first()
                                builder2.sendCardId = card.id
                                when (card.direction) {
                                    Left -> builder2.targetPlayerId =
                                        p.getAlternativeLocation(r.getNextLeftAlivePlayer().location)

                                    Right -> builder2.targetPlayerId =
                                        p.getAlternativeLocation(r.getNextRightAlivePlayer().location)

                                    Up -> {
                                        val target = g.players.filter { it !== p && it!!.alive }.random()!!
                                        builder2.targetPlayerId = p.getAlternativeLocation(target.location)
                                    }

                                    else -> {}
                                }
                                builder2.lockPlayerId = 0
                                builder2.seq = seq
                                g.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder2 = skill_leng_xue_xun_lian_b_tos.newBuilder()
                    val card = cards.find { it.isPureBlack() } ?: cards.find { it.isBlack() }
                    ?: if (r.identity == Black) cards.random()
                    else cards.find { r.identity in it.colors } ?: cards.random()
                    builder2.sendCardId = card.id
                    when (card.direction) {
                        Left -> {
                            builder2.targetPlayerId = r.getAlternativeLocation(r.getNextLeftAlivePlayer().location)
                            builder2.lockPlayerId = if (card.isBlack())
                                r.getAlternativeLocation(g.players.filter { it!!.alive && it.isEnemy(r) }
                                    .maxByOrNull { it!!.messageCards.count(Black) }?.location ?: 0
                                ) else 0
                        }

                        Right -> {
                            builder2.targetPlayerId = r.getAlternativeLocation(r.getNextRightAlivePlayer().location)
                            builder2.lockPlayerId = if (card.isBlack())
                                r.getAlternativeLocation(g.players.filter { it!!.alive && it.isEnemy(r) }
                                    .maxByOrNull { it!!.messageCards.count(Black) }?.location ?: 0
                                ) else 0
                        }

                        Up -> {
                            val target =
                                (if (card.isBlack()) g.players.filter { it!!.alive && it.isEnemy(r) }.randomOrNull()
                                else g.players.filter { it!!.alive && it.isPartner(r) }.randomOrNull())
                                    ?: g.players.filter { it !== r && it!!.alive }.random()!!
                            builder2.targetPlayerId = r.getAlternativeLocation(target.location)
                            builder2.lockPlayerId = if (card.isBlack()) builder2.targetPlayerId else 0
                        }

                        else -> {}
                    }
                    g.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            val pb = message as? skill_leng_xue_xun_lian_b_tos
            if (pb == null) {
                log.error("现在正在结算[冷血训练]")
                (player as? HumanPlayer)?.sendErrorMessage("现在正在结算[冷血训练]")
                return null
            }
            if (player !== r) {
                log.error("没有轮到你传情报")
                (player as? HumanPlayer)?.sendErrorMessage("没有轮到你传情报")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            val card = cards.find { it.id == pb.sendCardId }
            if (card == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                return null
            }
            if (!card.isBlack() && cards.any { card.isBlack() }) {
                log.error("你必须选择黑色牌")
                (player as? HumanPlayer)?.sendErrorMessage("你必须选择黑色牌")
                return null
            }
            val anotherCard = cards.first { it.id != pb.sendCardId }
            if (pb.targetPlayerId <= 0 || pb.targetPlayerId >= player.game!!.players.size) {
                log.error("目标错误: ${pb.targetPlayerId}")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误: ${pb.targetPlayerId}")
                return null
            }
            val target = player.game!!.players[player.getAbstractLocation(pb.targetPlayerId)]!!
            if (pb.lockPlayerId < 0 || pb.lockPlayerId >= player.game!!.players.size) {
                log.error("锁定目标错误: ${pb.lockPlayerId}")
                (player as? HumanPlayer)?.sendErrorMessage("锁定目标错误: ${pb.lockPlayerId}")
                return null
            }
            val lockPlayer = player.game!!.players[player.getAbstractLocation(pb.lockPlayerId)]!!
            val sendCardError = player.canSendCard(player, card, null, card.direction, target, listOf(lockPlayer))
            if (sendCardError != null) {
                log.error(sendCardError)
                (player as? HumanPlayer)?.sendErrorMessage(sendCardError)
                return null
            }
            player.incrSeq()
            log.info("${player}传出了${card}，方向是${card.direction}，传给了${target}，并锁定了[${lockPlayer}]")
            log.info("[调包]的被禁止使用了")
            log.info("${player}将${anotherCard}加入了手牌")
            player.game!!.players.forEach { it!!.skills += CannotPlayCard(cardType = listOf(Diao_Bao)) }
            player.cards.add(anotherCard)
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_leng_xue_xun_lian_b_toc.newBuilder()
                    builder.sendCard = card.toPbCard()
                    builder.senderId = p.getAlternativeLocation(player.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.lockPlayerId = p.getAlternativeLocation(lockPlayer.location)
                    builder.handCard = anotherCard.toPbCard()
                    p.send(builder.build())
                }
            }
            return ResolveResult(
                OnSendCard(
                    whoseTurn, r, card, card.direction, target, arrayOf(lockPlayer),
                    isMessageCardFaceUp = true, needRemoveCard = false, needNotify = false
                ), true
            )
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as executeLengXueXunLian

            if (whoseTurn != other.whoseTurn) return false
            if (r != other.r) return false
            if (!cards.contentEquals(other.cards)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = whoseTurn.hashCode()
            result = 31 * result + r.hashCode()
            result = 31 * result + cards.contentHashCode()
            return result
        }

        companion object {
            private val log = Logger.getLogger(executeLengXueXunLian::class.java)
        }
    }

    private class MustLockOne : SendMessageCanLockSkill, OneTurnSkill {
        override val skillId = SkillId.UNKNOWN

        override fun checkCanLock(card: Card, lockPlayers: List<Player>): Boolean {
            return lockPlayers.size == 1
        }
    }

    companion object {
        private val log = Logger.getLogger(LengXueXunLian::class.java)
        fun ai(e: SendPhaseStart, skill: ActiveSkill): Boolean {
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(
                    e.whoseTurn.game!!,
                    e.whoseTurn,
                    skill_leng_xue_xun_lian_a_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}