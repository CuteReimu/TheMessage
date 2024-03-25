package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.WeiBi
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Role.skill_ji_ban_a_tos
import com.fengsheng.protos.Role.skill_ji_ban_b_tos
import com.fengsheng.protos.skillJiBanAToc
import com.fengsheng.protos.skillJiBanATos
import com.fengsheng.protos.skillJiBanBToc
import com.fengsheng.protos.skillJiBanBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * SP顾小梦技能【羁绊】：出牌阶段限一次，可以摸两张牌，然后将至少一张手牌交给另一名角色。
 */
class JiBan : MainPhaseSkill() {
    override val skillId = SkillId.JI_BAN

    override val isInitialSkill = true

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[羁绊]一回合只能发动一次")
            r.sendErrorMessage("[羁绊]一回合只能发动一次")
            return
        }
        val pb = message as skill_ji_ban_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.resolve(ExecuteJiBan(g.fsm!!, r))
    }

    private data class ExecuteJiBan(val fsm: Fsm, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            logger.info("${r}发动了[羁绊]")
            r.draw(2)
            g.players.send { p ->
                skillJiBanAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq2 = p.seq
                        seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) autoSelect(seq2)
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) GameExecutor.post(g, { autoSelect(0) }, 1, TimeUnit.SECONDS)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_ji_ban_b_tos) {
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
            if (message.cardIdsCount == 0) {
                logger.error("至少需要选择一张卡牌")
                player.sendErrorMessage("至少需要选择一张卡牌")
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
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            val cards = List(message.cardIdsCount) {
                val card = r.findCard(message.getCardIds(it))
                if (card == null) {
                    logger.error("没有这张卡")
                    player.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            r.incrSeq()
            logger.info("${r}将${cards.joinToString()}交给$target")
            r.cards.removeAll(cards.toSet())
            target.cards.addAll(cards)
            g.players.send { p ->
                skillJiBanBToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target)
                        cards.forEach { this.cards.add(it.toPbCard()) }
                    else
                        unknownCardCount = cards.size
                }
            }
            g.addEvent(GiveCardEvent(r, r, target))
            return ResolveResult(fsm, true)
        }

        private fun autoSelect(seq: Int) {
            val availableTargets = r.game!!.players.filter { it!!.alive && it !== r } // 如果所有人都死了游戏就结束了，所以这里一定不为空
            val card =
                if (seq != 0) r.cards.random()
                else r.cards.bestCard(r.identity, true)
            val players =
                if (seq != 0) availableTargets
                else availableTargets.filter { r.isPartner(it!!) }.ifEmpty {
                    if (card.type in WeiBi.availableCardType) r.weiBiFailRate = 0
                    availableTargets
                } // 机器人优先选队友
            val player = players.random()!!
            r.game!!.tryContinueResolveProtocol(r, skillJiBanBTos {
                targetPlayerId = r.getAlternativeLocation(player.location)
                cardIds.add(card.id)
                this.seq = seq
            })
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.whoseTurn.getSkillUseCount(SkillId.JI_BAN) > 0) return false
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, skillJiBanATos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
