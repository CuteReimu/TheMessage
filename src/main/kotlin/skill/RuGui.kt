package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 老汉技能【如归】：你死亡前，可以将你情报区中的一张情报置入当前回合角色的情报区中。
 */
class RuGui : TriggeredSkill, BeforeDieSkill {
    override val skillId = SkillId.RU_GUI

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<PlayerDieEvent>(this) { event ->
            askWhom === event.whoDie || return@findEvent false
            askWhom !== event.whoseTurn || return@findEvent false
            event.whoseTurn.alive || return@findEvent false
            askWhom.messageCards.isNotEmpty()
        } ?: return null
        return ResolveResult(executeRuGui(g.fsm!!, event, askWhom), true)
    }

    private data class executeRuGui(val fsm: Fsm, val event: PlayerDieEvent, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    val builder = skill_wait_for_ru_gui_toc.newBuilder()
                    builder.playerId = player.getAlternativeLocation(r.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        builder.seq = seq
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (r.checkSeq(seq)) {
                                val builder2 = skill_ru_gui_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq
                                r.game!!.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, player.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    player.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                var value = 0
                var card: Card? = null
                for (c in r.messageCards) {
                    val v = r.calculateMessageCardValue(event.whoseTurn, event.whoseTurn, c)
                    if (v >= value) {
                        value = v
                        card = c
                    }
                }
                GameExecutor.post(
                    r.game!!,
                    {
                        val builder = skill_ru_gui_tos.newBuilder()
                        if (card != null) {
                            builder.enable = true
                            builder.cardId = card.id
                        } else {
                            builder.enable = false
                        }
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    },
                    2,
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
            if (message !is skill_ru_gui_tos) {
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
                    (p as? HumanPlayer)?.send(skill_ru_gui_toc.newBuilder().setEnable(false).build())
                }
                return ResolveResult(fsm, true)
            }
            val card = r.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                return null
            }
            val target = event.whoseTurn
            if (!target.alive) {
                logger.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[如归]")
            r.deleteMessageCard(card.id)
            target.messageCards.add(card)
            logger.info("${r}面前的${card}移到了${target}面前")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_ru_gui_toc.newBuilder()
                    builder.enable = true
                    builder.cardId = card.id
                    builder.playerId = p.getAlternativeLocation(r.location)
                    p.send(builder.build())
                }
            }
            g.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }
    }
}