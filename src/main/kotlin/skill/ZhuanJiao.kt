package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_zhuan_jiao_tos
import com.fengsheng.protos.skillWaitForZhuanJiaoToc
import com.fengsheng.protos.skillZhuanJiaoToc
import com.fengsheng.protos.skillZhuanJiaoTos
import com.google.protobuf.GeneratedMessage
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
        return ResolveResult(ExecuteZhuanJiao(g.fsm!!, event.whoseTurn, askWhom), true)
    }

    private data class ExecuteZhuanJiao(val fsm: Fsm, val whoseTurn: Player, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            r.game!!.players.send { player ->
                skillWaitForZhuanJiaoToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq2 = player.seq
                        seq = seq2
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (r.checkSeq(seq2))
                                r.game!!.tryContinueResolveProtocol(r, skillZhuanJiaoTos { seq = seq2 })
                        }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
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
                            r.game!!.tryContinueResolveProtocol(r, skillZhuanJiaoTos {
                                targetPlayerId = r.getAlternativeLocation(target.location)
                                enable = true
                                cardId = messageCard.id
                            })
                        }, 3, TimeUnit.SECONDS)
                        return null
                    }
                }
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillZhuanJiaoTos {})
                }, 1, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_zhuan_jiao_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
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
                player.sendErrorMessage("没有这张卡")
                return null
            }
            if (card.isBlack()) {
                logger.error("不是非黑色情报")
                player.sendErrorMessage("不是非黑色情报")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            if (message.targetPlayerId == 0) {
                logger.error("不能以自己为目标")
                player.sendErrorMessage("不能以自己为目标")
                return null
            }
            val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            if (target.checkThreeSameMessageCard(card)) {
                logger.error("你不能通过此技能让任何角色收集三张或更多同色情报")
                player.sendErrorMessage("你不能通过此技能让任何角色收集三张或更多同色情报")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[转交]")
            r.deleteMessageCard(card.id)
            target.messageCards.add(card)
            logger.info("${r}面前的${card}移到了${target}面前")
            g.players.send {
                skillZhuanJiaoToc {
                    cardId = card.id
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
            r.draw(2)
            g.addEvent(AddMessageCardEvent(whoseTurn))
            return ResolveResult(fsm, true)
        }
    }
}
