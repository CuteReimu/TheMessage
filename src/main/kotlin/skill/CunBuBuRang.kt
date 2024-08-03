package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.protos.Role.skill_cun_bu_bu_rang_tos
import com.fengsheng.protos.skillCunBuBuRangToc
import com.fengsheng.protos.skillCunBuBuRangTos
import com.fengsheng.protos.skillWaitForCunBuBuRangToc
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 凌素秋技能【寸步不让】：在其他角色获得你的手牌结算之后，你可以抽该角色一张手牌。
 */
class CunBuBuRang : TriggeredSkill {
    override val skillId = SkillId.CUN_BU_BU_RANG

    override val isInitialSkill = true

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val event = g.findEvent<GiveCardEvent>(this) { event ->
            askWhom === event.fromPlayer || return@findEvent false
            askWhom !== event.toPlayer || return@findEvent false
            event.toPlayer.alive || return@findEvent false
            event.toPlayer.cards.isNotEmpty()
        }
        if (event != null)
            return ResolveResult(ExecuteCunBuBuRang(g.fsm!!, askWhom, event.toPlayer), true)
        return null
    }

    private data class ExecuteCunBuBuRang(
        val fsm: Fsm,
        val r: Player,
        val target: Player
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            for (player in r.game!!.players) {
                if (player is HumanPlayer) {
                    if (player === r) {
                        // 晚一秒提示凌素秋，以防客户端动画bug
                        GameExecutor.post(r.game!!, {
                            player.send(skillWaitForCunBuBuRangToc {
                                playerId = player.getAlternativeLocation(r.location)
                                targetPlayerId = player.getAlternativeLocation(target.location)
                                waitingSecond = Config.WaitSecond
                                val seq = player.seq
                                this.seq = seq
                                player.timeout = GameExecutor.post(r.game!!, {
                                    if (r.checkSeq(seq)) {
                                        r.game!!.tryContinueResolveProtocol(r, skillCunBuBuRangTos {
                                            enable = true
                                            this.seq = seq
                                        })
                                    }
                                }, player.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                            })
                        }, 1, TimeUnit.SECONDS)
                    } else {
                        player.send(skillWaitForCunBuBuRangToc {
                            playerId = player.getAlternativeLocation(r.location)
                            targetPlayerId = player.getAlternativeLocation(target.location)
                            waitingSecond = Config.WaitSecond
                        })
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, skillCunBuBuRangTos { enable = true })
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
            if (message !is skill_cun_bu_bu_rang_tos) {
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
                g.players.send { skillCunBuBuRangToc { enable = false } }
                return ResolveResult(fsm, true)
            }
            val card = target.cards.random()
            r.incrSeq()
            logger.info("${r}对${target}发动了[寸步不让]，抽取了$card")
            target.deleteCard(card.id)
            r.cards.add(card)
            g.players.send {
                skillCunBuBuRangToc {
                    playerId = it.getAlternativeLocation(r.location)
                    enable = true
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    if (it === r || it === target) this.card = card.toPbCard()
                }
            }
            g.addEvent(GiveCardEvent(whoseTurn, target, r))
            return ResolveResult(fsm, true)
        }
    }
}
