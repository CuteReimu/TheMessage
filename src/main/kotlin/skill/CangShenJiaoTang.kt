package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.sortCards
import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.card.filter
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_cang_shen_jiao_tang_b_tos
import com.fengsheng.protos.Role.skill_cang_shen_jiao_tang_c_tos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 玛利亚技能【藏身教堂】：当你传出的情报被接受后，若接收者是隐藏角色，则你摸一张牌（摸牌走摸牌协议），并可以将该角色翻至面朝下；若是公开角色，则可以将其一张黑色情报加入你的手牌或置入你的情报区。
 */
class CangShenJiaoTang : TriggeredSkill {
    override val skillId = SkillId.CANG_SHEN_JIAO_TANG

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<ReceiveCardEvent>(this) { event ->
            askWhom === event.sender
        } ?: return null
        val target = event.inFrontOfWhom
        val isHiddenRole = !target.isPublicRole
        val timeoutSecond = Config.WaitSecond
        g.players.send { player ->
            skillCangShenJiaoTangAToc {
                playerId = player.getAlternativeLocation(askWhom.location)
                targetPlayerId = player.getAlternativeLocation(target.location)
                this.isHiddenRole = isHiddenRole
                if (isHiddenRole && target.roleFaceUp || !isHiddenRole && target.messageCards.any { it.isBlack() }) {
                    waitingSecond = timeoutSecond
                    if (player === askWhom) seq = player.seq
                }
            }
        }
        if (isHiddenRole) {
            logger.info("${askWhom}发动了[藏身教堂]")
            askWhom.draw(1)
        }
        if (isHiddenRole && target.roleFaceUp)
            return ResolveResult(executeCangShenJiaoTangB(g.fsm!!, event, timeoutSecond), true)
        if (!isHiddenRole && target.messageCards.count(Black) > 0)
            return ResolveResult(executeCangShenJiaoTangC(g.fsm!!, event, timeoutSecond), true)
        return null
    }

    private data class executeCangShenJiaoTangB(
        val fsm: Fsm,
        val event: ReceiveCardEvent,
        val timeoutSecond: Int
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.sender
            if (r is HumanPlayer) {
                val seq2 = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq2))
                        r.game!!.tryContinueResolveProtocol(r, skillCangShenJiaoTangBTos { seq = seq2 })
                }, r.getWaitSeconds(timeoutSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillCangShenJiaoTangBTos {
                        enable = r.isPartnerOrSelf(event.inFrontOfWhom)
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.sender) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_cang_shen_jiao_tang_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            player.incrSeq()
            val target = event.inFrontOfWhom
            if (message.enable) player.game!!.playerSetRoleFaceUp(target, false)
            player.game!!.players.send {
                skillCangShenJiaoTangBToc {
                    enable = message.enable
                    playerId = it.getAlternativeLocation(player.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    private data class executeCangShenJiaoTangC(
        val fsm: Fsm,
        val event: ReceiveCardEvent,
        val timeoutSecond: Int
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = event.sender
            if (r is HumanPlayer) {
                val seq2 = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq2)) {
                        r.game!!.tryContinueResolveProtocol(r, skillCangShenJiaoTangCTos {
                            enable = false
                            seq = seq2
                        })
                    }
                }, r.getWaitSeconds(timeoutSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    var value = 0
                    var selectedCard: Card? = null
                    var asMessageCard = false
                    val messageCards = event.inFrontOfWhom.messageCards.filter(Black).sortCards(r.identity)
                    for (messageCard in messageCards) {
                        val newValue1 = r.calculateRemoveCardValue(event.whoseTurn, event.inFrontOfWhom, messageCard)
                        if (newValue1 + 10 >= value) {
                            value = newValue1 + 10
                            selectedCard = messageCard
                            asMessageCard = false
                        }
                        r !== event.inFrontOfWhom || continue
                        val newValue2 = r.calculateMessageCardValue(event.whoseTurn, r, messageCard)
                        if (newValue1 + newValue2 > value) {
                            value = newValue1 + newValue2
                            selectedCard = messageCard
                            asMessageCard = true
                        }
                    }
                    r.game!!.tryContinueResolveProtocol(r, skillCangShenJiaoTangCTos {
                        if (selectedCard != null) {
                            enable = true
                            cardId = selectedCard.id
                            this.asMessageCard = asMessageCard
                        }
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== event.sender) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_cang_shen_jiao_tang_c_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            val target = event.inFrontOfWhom
            if (message.enable) {
                val card = target.findMessageCard(message.cardId)
                if (card == null) {
                    logger.error("没有这张情报")
                    player.sendErrorMessage("没有这张情报")
                    return null
                }
                if (!card.isBlack()) {
                    logger.error("目标情报不是黑色的")
                    player.sendErrorMessage("目标情报不是黑色的")
                    return null
                }
                if (message.asMessageCard) {
                    logger.info("${player}发动了[藏身教堂]，将${target}面前的${card}移到自己面前")
                    target.deleteMessageCard(message.cardId)
                    player.messageCards.add(card)
                    player.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
                } else {
                    logger.info("${player}发动了[藏身教堂]，将${target}面前的${card}加入了手牌")
                    target.deleteMessageCard(message.cardId)
                    player.cards.add(card)
                }
            }
            player.incrSeq()
            player.game!!.players.send {
                skillCangShenJiaoTangCToc {
                    enable = message.enable
                    playerId = it.getAlternativeLocation(player.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    cardId = message.cardId
                    asMessageCard = message.asMessageCard
                }
            }
            return ResolveResult(fsm, true)
        }
    }
}
