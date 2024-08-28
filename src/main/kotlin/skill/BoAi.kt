package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.filterByRole
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.skill_bo_ai_a_tos
import com.fengsheng.protos.Role.skill_bo_ai_b_tos
import com.fengsheng.protos.skillBoAiAToc
import com.fengsheng.protos.skillBoAiATos
import com.fengsheng.protos.skillBoAiBToc
import com.fengsheng.protos.skillBoAiBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 白沧浪技能【博爱】：出牌阶段限一次，你可以摸一张牌，然后可以将一张手牌交给另一名角色，若交给了女性角色，则你再摸一张牌。
 */
class BoAi : MainPhaseSkill() {
    override val skillId = SkillId.BO_AI

    override val isInitialSkill = true

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[博爱]一回合只能发动一次")
            r.sendErrorMessage("[博爱]一回合只能发动一次")
            return
        }
        val pb = message as skill_bo_ai_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(ExecuteBoAi(g.fsm!!, r))
    }

    private data class ExecuteBoAi(val fsm: Fsm, val r: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            logger.info("${r}发动了[博爱]")
            g.players.send { p ->
                skillBoAiAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    waitingSecond = g.waitSecond
                    if (p === r) {
                        val seq2 = p.seq
                        seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2))
                                r.game!!.tryContinueResolveProtocol(r, skillBoAiBTos { seq = seq2 })
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            r.draw(1)
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    r.game!!.tryContinueResolveProtocol(r, skillBoAiBTos {
                        if (!g.isEarly) {
                            val player = r.game!!.players.find { it!!.alive && it.isPartner(r) && it.isFemale }
                            if (player != null) {
                                targetPlayerId = r.getAlternativeLocation(player.location)
                                val chosenCard = r.cards.filterByRole(player.role)
                                cardId =
                                    if (chosenCard.isEmpty()) r.cards.bestCard(r.identity, true).id
                                    else chosenCard.random().id
                            }
                        }
                    })
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
            if (message !is skill_bo_ai_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.cardId == 0) {
                r.incrSeq()
                g.players.send {
                    skillBoAiBToc {
                        playerId = it.getAlternativeLocation(r.location)
                        enable = false
                    }
                }
                return ResolveResult(fsm, true)
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
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            val card = r.findCard(message.cardId)
            if (card == null) {
                logger.error("没有这张卡")
                player.sendErrorMessage("没有这张卡")
                return null
            }
            r.incrSeq()
            logger.info("${r}将${card}交给$target")
            r.deleteCard(card.id)
            target.cards.add(card)
            g.players.send {
                skillBoAiBToc {
                    playerId = it.getAlternativeLocation(r.location)
                    enable = true
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    if (it === r || it === target) this.card = card.toPbCard()
                }
            }
            if (target.isFemale) r.draw(1)
            g.addEvent(GiveCardEvent(r, r, target))
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.whoseTurn.getSkillUseCount(SkillId.BO_AI) == 0 || return false
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, skillBoAiATos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
