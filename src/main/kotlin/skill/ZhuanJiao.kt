package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 白小年技能【转交】：你使用一张手牌后，可以从你的情报区选择一张非黑色情报，将其置入另一名角色的情报区，然后你摸两张牌。你不能通过此技能让任何角色收集三张或更多同色情报。
 */
class ZhuanJiao : TriggeredSkill {
    override val skillId = SkillId.ZHUAN_JIAO

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<FinishResolveCardEvent>(this) { event ->
            askWhom === event.player || return@findEvent false
            askWhom.alive || return@findEvent false
            askWhom.messageCards.any { !it.isBlack() }
        } ?: return null
        return ResolveResult(executeZhuanJiao(g.fsm!!, event.whoseTurn, askWhom), true)
    }

    private data class executeZhuanJiao(val fsm: Fsm, val whoseTurn: Player, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_zhuan_jiao_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq2 = player.seq
                        builder.seq = seq2
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (r.checkSeq(seq2)) {
                                val builder2 = skill_zhuan_jiao_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq2
                                r.game!!.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                for (messageCard in r.messageCards) {
                    !messageCard.isBlack() || continue
                    val players = r.game!!.players.filter { p ->
                        if (p === r || !p!!.alive) return@filter false
                        if (r.identity == Black) {
                            if (p.identity in messageCard.colors) return@filter false
                        } else {
                            if (p.isEnemy(r)) return@filter false
                        }
                        !p.checkThreeSameMessageCard(messageCard)
                    }
                    if (players.isNotEmpty()) {
                        val target = players[Random.nextInt(players.size)]!!
                        GameExecutor.post(r.game!!, {
                            val builder = skill_zhuan_jiao_tos.newBuilder()
                            builder.targetPlayerId = r.getAlternativeLocation(target.location)
                            builder.enable = true
                            builder.cardId = messageCard.id
                            r.game!!.tryContinueResolveProtocol(r, builder.build())
                        }, 3, TimeUnit.SECONDS)
                        return null
                    }
                }
                GameExecutor.post(
                    r.game!!,
                    {
                        val builder = skill_zhuan_jiao_tos.newBuilder()
                        builder.enable = false
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    },
                    1,
                    TimeUnit.SECONDS
                )
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_zhuan_jiao_tos) {
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
                return ResolveResult(fsm, true)
            }
            val card = r.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            if (card.isBlack()) {
                logger.error("不是非黑色情报")
                (player as? HumanPlayer)?.sendErrorMessage("不是非黑色情报")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            if (message.targetPlayerId == 0) {
                logger.error("不能以自己为目标")
                (player as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
                return null
            }
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            if (target.checkThreeSameMessageCard(card)) {
                logger.error("你不能通过此技能让任何角色收集三张或更多同色情报")
                (player as? HumanPlayer)?.sendErrorMessage("你不能通过此技能让任何角色收集三张或更多同色情报")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[转交]")
            r.deleteMessageCard(card.id)
            target.messageCards.add(card)
            logger.info("${r}面前的${card}移到了${target}面前")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_zhuan_jiao_toc.newBuilder()
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            r.draw(2)
            g.addEvent(AddMessageCardEvent(whoseTurn))
            return ResolveResult(fsm, true)
        }
    }
}