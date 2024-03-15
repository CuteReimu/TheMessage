package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.protos.Role.skill_ru_gui_tos
import com.fengsheng.protos.skillRuGuiToc
import com.fengsheng.protos.skillRuGuiTos
import com.fengsheng.protos.skillWaitForRuGuiToc
import com.google.protobuf.GeneratedMessage
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
            r.game!!.players.send { player ->
                skillWaitForRuGuiToc {
                    playerId = player.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (player === r) {
                        val seq = player.seq
                        this.seq = seq
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (r.checkSeq(seq))
                                r.game!!.tryContinueResolveProtocol(r, skillRuGuiTos { this.seq = seq })
                        }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                var value = -1
                var card: Card? = null
                for (c in r.messageCards) {
                    val v = r.calculateMessageCardValue(event.whoseTurn, event.whoseTurn, c)
                    if (v > value) {
                        value = v
                        card = c
                    }
                }
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillRuGuiTos {
                        card?.let {
                            enable = true
                            cardId = it.id
                        }
                    })
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_ru_gui_tos) {
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
                g.players.send { skillRuGuiToc {} }
                return ResolveResult(fsm, true)
            }
            val card = r.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                player.sendErrorMessage("没有这张卡")
                return null
            }
            val target = event.whoseTurn
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            logger.info("${r}发动了[如归]")
            r.deleteMessageCard(card.id)
            target.messageCards.add(card)
            logger.info("${r}面前的${card}移到了${target}面前")
            g.players.send {
                skillRuGuiToc {
                    enable = true
                    cardId = card.id
                    playerId = it.getAlternativeLocation(r.location)
                }
            }
            g.addEvent(AddMessageCardEvent(event.whoseTurn))
            return ResolveResult(fsm, true)
        }
    }
}