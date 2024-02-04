package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.count
import com.fengsheng.card.filter
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
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
        for (player in g.players) {
            if (player is HumanPlayer) {
                val builder = skill_cang_shen_jiao_tang_a_toc.newBuilder()
                builder.playerId = player.getAlternativeLocation(askWhom.location)
                builder.targetPlayerId = player.getAlternativeLocation(target.location)
                builder.isHiddenRole = isHiddenRole
                if (isHiddenRole && target.roleFaceUp || !isHiddenRole && target.messageCards.count(Black) > 0) {
                    builder.waitingSecond = timeoutSecond
                    if (player === askWhom) builder.seq = player.seq
                }
                player.send(builder.build())
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
                    if (r.checkSeq(seq2)) {
                        val builder = skill_cang_shen_jiao_tang_b_tos.newBuilder()
                        builder.enable = false
                        builder.seq = seq2
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(timeoutSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = skill_cang_shen_jiao_tang_b_tos.newBuilder()
                    builder.enable = r.isPartnerOrSelf(event.inFrontOfWhom)
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.sender) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_cang_shen_jiao_tang_b_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
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
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_cang_shen_jiao_tang_b_toc.newBuilder()
                    builder.enable = message.enable
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
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
                        val builder = skill_cang_shen_jiao_tang_c_tos.newBuilder()
                        builder.enable = false
                        builder.seq = seq2
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(timeoutSecond + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = skill_cang_shen_jiao_tang_c_tos.newBuilder()
                    val take =
                        if (r === event.inFrontOfWhom)
                            !(r.identity == Black && r.secretTask in listOf(Killer, Pioneer, Sweeper))
                        else if (r.isPartner(event.inFrontOfWhom))
                            true
                        else
                            r.identity == Black && r.secretTask in listOf(Killer, Pioneer, Sweeper)
                    if (take) {
                        builder.enable = true
                        builder.cardId = event.inFrontOfWhom.messageCards.filter(Black).bestCard(r.identity).id
                    }
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== event.sender) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_cang_shen_jiao_tang_c_tos) {
                logger.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
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
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张情报")
                    return null
                }
                if (!card.isBlack()) {
                    logger.error("目标情报不是黑色的")
                    (player as? HumanPlayer)?.sendErrorMessage("目标情报不是黑色的")
                    return null
                }
                if (message.asMessageCard) {
                    if (target === player) {
                        logger.error("你不能把情报从自己面前移到自己面前")
                        (player as? HumanPlayer)?.sendErrorMessage("你不能把情报从自己面前移到自己面前")
                        return null
                    }
                    logger.info("${player}发动了[藏身教堂]，将${target}面前的${card}移到自己面前")
                    target.deleteMessageCard(message.cardId)
                    player.messageCards.add(card)
                } else {
                    logger.info("${player}发动了[藏身教堂]，将${target}面前的${card}加入了手牌")
                    target.deleteMessageCard(message.cardId)
                    player.cards.add(card)
                }
            }
            player.incrSeq()
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_cang_shen_jiao_tang_c_toc.newBuilder()
                    builder.enable = message.enable
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.cardId = message.cardId
                    builder.asMessageCard = message.asMessageCard
                    p.send(builder.build())
                }
            }
            player.game!!.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }
    }
}