package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.MiLing.ExecuteMiLing
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnSendCard
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Common.card_type.Diao_Bao
import com.fengsheng.protos.Common.card_type.Mi_Ling
import com.fengsheng.protos.Common.direction.*
import com.fengsheng.protos.Role.skill_leng_xue_xun_lian_a_tos
import com.fengsheng.protos.Role.skill_leng_xue_xun_lian_b_tos
import com.fengsheng.protos.skillLengXueXunLianAToc
import com.fengsheng.protos.skillLengXueXunLianATos
import com.fengsheng.protos.skillLengXueXunLianBToc
import com.fengsheng.protos.skillLengXueXunLianBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * SP韩梅技能【冷血训练】：你需要传出情报时，可以改为展示牌堆顶的两张牌，从中选择一张（若有黑色牌则必须选择一张黑色牌）作为情报面朝上传出，并锁定一名角色，且令所有角色本回合中不能使用【调包】，之后将未选择的那张加入你的手牌。
 */
class LengXueXunLian : ActiveSkill {
    override val skillId = SkillId.LENG_XUE_XUN_LIAN

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = false

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        message as skill_leng_xue_xun_lian_a_tos
        if (r is HumanPlayer && !r.checkSeq(message.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = g.fsm
        if (fsm is SendPhaseStart && r === fsm.whoseTurn) {
            g.resolve(ExecuteLengXueXunLian(fsm.whoseTurn, fsm.whoseTurn, g.deck.draw(2)))
        } else if (fsm is ExecuteMiLing && r === fsm.target) {
            g.resolve(
                OnFinishResolveCard(
                    fsm.sendPhase.whoseTurn,
                    fsm.sendPhase.whoseTurn,
                    fsm.target,
                    fsm.card,
                    Mi_Ling,
                    ExecuteLengXueXunLian(fsm.sendPhase.whoseTurn, fsm.target, g.deck.draw(2)),
                    discardAfterResolve = false
                )
            )
        } else {
            logger.error("现在不能发动[冷血训练]")
            r.sendErrorMessage("现在不能发动[冷血训练]")
        }
    }

    private data class ExecuteLengXueXunLian(
        override val whoseTurn: Player,
        val r: Player,
        val cards: List<Card>
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            r.incrSeq()
            logger.info("${r}发动了[冷血训练]，展示了牌堆顶的${cards.joinToString()}")
            r.skills += MustLockOne()
            g.players.send { p ->
                skillLengXueXunLianAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    this@ExecuteLengXueXunLian.cards.forEach { cards.add(it.toPbCard()) }
                    waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                g.tryContinueResolveProtocol(p, skillLengXueXunLianBTos {
                                    val card = this@ExecuteLengXueXunLian.cards.minBy { !it.isBlack() }
                                    sendCardId = card.id
                                    when (card.direction) {
                                        Left -> targetPlayerId =
                                            p.getAlternativeLocation(r.getNextLeftAlivePlayer().location)

                                        Right -> targetPlayerId =
                                            p.getAlternativeLocation(r.getNextRightAlivePlayer().location)

                                        Up -> {
                                            val target = g.players.filter { it !== p && it!!.alive }.random()!!
                                            targetPlayerId = p.getAlternativeLocation(target.location)
                                        }

                                        else -> {}
                                    }
                                    lockPlayerId = 0
                                    this.seq = seq
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val availableCards = cards.filter { it.isBlack() }.ifEmpty { cards }
                    val result = r.calSendMessageCard(r, availableCards)
                    g.tryContinueResolveProtocol(r, skillLengXueXunLianBTos {
                        sendCardId = result.card.id
                        targetPlayerId = r.getAlternativeLocation(result.target.location)
                        if (result.lockedPlayers.isNotEmpty())
                            lockPlayerId = r.getAlternativeLocation(result.lockedPlayers.first().location)
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            val pb = message as? skill_leng_xue_xun_lian_b_tos
            if (pb == null) {
                logger.error("现在正在结算[冷血训练]")
                player.sendErrorMessage("现在正在结算[冷血训练]")
                return null
            }
            if (player !== r) {
                logger.error("没有轮到你传情报")
                player.sendErrorMessage("没有轮到你传情报")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            val card = cards.find { it.id == pb.sendCardId }
            if (card == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            if (!card.isBlack() && cards.any { card.isBlack() }) {
                logger.error("你必须选择黑色牌")
                player.sendErrorMessage("你必须选择黑色牌")
                return null
            }
            val anotherCard = cards.first { it.id != pb.sendCardId }
            if (pb.targetPlayerId <= 0 || pb.targetPlayerId >= player.game!!.players.size) {
                logger.error("目标错误: ${pb.targetPlayerId}")
                player.sendErrorMessage("目标错误: ${pb.targetPlayerId}")
                return null
            }
            val target = player.game!!.players[player.getAbstractLocation(pb.targetPlayerId)]!!
            if (pb.lockPlayerId < 0 || pb.lockPlayerId >= player.game!!.players.size) {
                logger.error("锁定目标错误: ${pb.lockPlayerId}")
                player.sendErrorMessage("锁定目标错误: ${pb.lockPlayerId}")
                return null
            }
            val lockPlayer = player.game!!.players[player.getAbstractLocation(pb.lockPlayerId)]!!
            val sendCardError = player.canSendCard(player, card, null, card.direction, target, listOf(lockPlayer))
            if (sendCardError != null) {
                logger.error(sendCardError)
                player.sendErrorMessage(sendCardError)
                return null
            }
            player.incrSeq()
            logger.info("${player}传出了$card，方向是${card.direction}，传给了$target，并锁定了[$lockPlayer]")
            logger.info("[调包]的被禁止使用了")
            logger.info("${player}将${anotherCard}加入了手牌")
            player.game!!.players.forEach { it!!.skills += CannotPlayCard(cardType = listOf(Diao_Bao)) }
            player.cards.add(anotherCard)
            player.game!!.players.send {
                skillLengXueXunLianBToc {
                    sendCard = card.toPbCard()
                    senderId = it.getAlternativeLocation(player.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    lockPlayerId = it.getAlternativeLocation(lockPlayer.location)
                    handCard = anotherCard.toPbCard()
                }
            }
            return ResolveResult(
                OnSendCard(
                    whoseTurn, r, card, card.direction, target, listOf(lockPlayer),
                    isMessageCardFaceUp = true, needRemoveCard = false, needNotify = false
                ), true
            )
        }
    }

    class MustLockOne : SendMessageCanLockSkill, OneTurnSkill {
        override val skillId = SkillId.UNKNOWN

        override val isInitialSkill = false

        override fun checkCanLock(card: Card, lockPlayers: List<Player>): Boolean {
            return lockPlayers.size == 1
        }
    }

    companion object {
        fun ai(e: SendPhaseStart, skill: ActiveSkill): Boolean {
            e.whoseTurn.cards.isEmpty() || e.whoseTurn.calSendMessageCard().value <= 111 || return false
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, skillLengXueXunLianATos { })
            }, 1, TimeUnit.SECONDS)
            return true
        }
    }
}
