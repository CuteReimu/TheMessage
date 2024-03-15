package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import com.fengsheng.protos.Role.skill_mi_xin_a_tos
import com.fengsheng.protos.Role.skill_mi_xin_b_tos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 成年韩梅技能【密信】：接收其他角色情报后，可以翻开此角色，摸两张牌，然后将一张含该情报不同颜色的手牌置入传出者的情报区。
 */
class MiXin : TriggeredSkill {
    override val skillId = SkillId.MI_XIN

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.inFrontOfWhom || return@findEvent false
            askWhom !== event.sender || return@findEvent false
            !askWhom.roleFaceUp
        } ?: return null
        val color = event.messageCard.colors
        return ResolveResult(executeMiXinA(g.fsm!!, event) { card -> card.colors.any { it !in color } }, true)
    }

    private data class executeMiXinA(
        val fsm: Fsm,
        val event: ReceiveCardEvent,
        val checkCard: (Card) -> Boolean
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in event.whoseTurn.game!!.players) {
                if (p === event.inFrontOfWhom)
                    p.notifyReceivePhase(event.whoseTurn, event.inFrontOfWhom, event.messageCard, event.inFrontOfWhom)
                else if (p is HumanPlayer)
                    p.send(unknownWaitingToc { waitingSecond = Config.WaitSecond })
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
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
            if (message !is skill_mi_xin_a_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val r = event.inFrontOfWhom
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            r.incrSeq()
            g.playerSetRoleFaceUp(r, true)
            val target = event.sender
            logger.info("${r}发动了[密信]")
            r.draw(2)
            val hasNext = target.alive && r.cards.any(checkCard)
            g.players.send { p ->
                skillMiXinAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    messageCard = event.messageCard.toPbCard()
                    if (hasNext) {
                        waitingSecond = Config.WaitSecond
                        if (p === r) seq = p.seq
                    }
                }
            }
            if (!hasNext)
                return ResolveResult(fsm, true)
            return ResolveResult(executeMiXinB(fsm, event, checkCard), true)
        }
    }

    private data class executeMiXinB(val fsm: Fsm, val event: ReceiveCardEvent, val checkCard: (Card) -> Boolean) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.inFrontOfWhom
            if (r is HumanPlayer) {
                val card = r.cards.filter(checkCard).random()
                val seq = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq)) {
                        r.game!!.tryContinueResolveProtocol(r, skillMiXinBTos {
                            cardId = card.id
                            this.seq = seq
                        })
                    }
                }, r.getWaitSeconds(Config.WaitSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                var value = Int.MIN_VALUE
                var card = r.cards.first(checkCard)
                for (c in r.cards.sortCards(r.identity, true)) {
                    checkCard(c) || continue
                    val v = r.calculateMessageCardValue(event.whoseTurn, event.sender, c)
                    if (v > value) {
                        value = v
                        card = c
                    }
                }
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillMiXinBTos { cardId = card.id })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.inFrontOfWhom) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_mi_xin_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
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
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            if (!checkCard(card)) {
                logger.error("选择的牌不含有相同颜色")
                player.sendErrorMessage("选择的牌不含有不同颜色")
                return null
            }
            r.incrSeq()
            val target = event.sender
            logger.info("${r}将${card}置入${target}的情报区")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            g.players.send {
                skillMiXinBToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    this.card = card.toPbCard()
                }
            }
            g.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(fsm0: Fsm): Boolean {
            if (fsm0 !is executeMiXinA) return false
            val p = fsm0.event.inFrontOfWhom
            val target = fsm0.event.sender
            val card = fsm0.event.messageCard
            if (card.colors.size == 2) {
                val color = listOf(Black, Red, Blue).filter { it !in card.colors }
                if (p.game!!.players.any {
                        it!!.isEnemy(p) && it.willWin(fsm0.event.whoseTurn, target, color)
                    } || target.isPartnerOrSelf(p) && target.willDie(color)) return false
            }
            GameExecutor.post(p.game!!, {
                p.game!!.tryContinueResolveProtocol(p, skillMiXinATos { })
            }, 1, TimeUnit.SECONDS)
            return true
        }
    }
}