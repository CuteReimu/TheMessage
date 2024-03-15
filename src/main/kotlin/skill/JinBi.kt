package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Role.skill_jin_bi_a_tos
import com.fengsheng.protos.Role.skill_jin_bi_b_tos
import com.fengsheng.protos.skillJinBiAToc
import com.fengsheng.protos.skillJinBiATos
import com.fengsheng.protos.skillJinBiBToc
import com.fengsheng.protos.skillJinBiBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 王田香技能【禁闭】：出牌阶段限一次，你可以指定一名角色，除非其交给你两张手牌，否则其本回合不能使用手牌，且所有角色技能无效。
 */
class JinBi : MainPhaseSkill() {
    override val skillId = SkillId.JIN_BI

    override val isInitialSkill = true

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[禁闭]一回合只能发动一次")
            r.sendErrorMessage("[禁闭]一回合只能发动一次")
            return
        }
        val pb = message as skill_jin_bi_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            r.sendErrorMessage("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            logger.error("不能以自己为目标")
            r.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}对${target}发动了[禁闭]")
        g.resolve(executeJinBi(g.fsm!!, r, target))
    }

    private data class executeJinBi(val fsm: Fsm, val r: Player, val target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (target.cards.size < 2) {
                doExecuteJinBi()
                return ResolveResult(fsm, true)
            }
            r.game!!.players.send { p ->
                skillJinBiAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    waitingSecond = Config.WaitSecond
                    if (p === target) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(p.game!!, {
                            if (p.checkSeq(seq))
                                p.game!!.tryContinueResolveProtocol(p, skillJinBiBTos { this.seq = seq })
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (target is RobotPlayer)
                GameExecutor.post(target.game!!, {
                    target.game!!.tryContinueResolveProtocol(target, skillJinBiBTos {})
                }, 1, TimeUnit.SECONDS)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== target) {
                logger.error("你不是被禁闭的目标")
                player.sendErrorMessage("你不是被禁闭的目标")
                return null
            }
            if (message !is skill_jin_bi_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = target.game!!
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${target.seq}, actual Seq: ${message.seq}")
                target.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.cardIdsCount == 0) {
                target.incrSeq()
                doExecuteJinBi()
                return ResolveResult(fsm, true)
            } else if (message.cardIdsCount != 2) {
                logger.error("给的牌数量不对：${message.cardIdsCount}")
                player.sendErrorMessage("给的牌数量不对：${message.cardIdsCount}")
                return null
            }
            val cards = List(2) {
                val card = target.findCard(message.getCardIds(it))
                if (card == null) {
                    logger.error("没有这张牌")
                    player.sendErrorMessage("没有这张牌")
                    return null
                }
                card
            }
            target.incrSeq()
            target.cards.removeAll(cards.toSet())
            r.cards.addAll(cards)
            logger.info("${target}给了${r}${cards.joinToString()}")
            g.players.send { p ->
                skillJinBiBToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target)
                        cards.forEach { this.cards.add(it.toPbCard()) }
                    else
                        unknownCardCount = 2
                }
            }
            g.addEvent(GiveCardEvent(r, target, r))
            return ResolveResult(fsm, true)
        }

        private fun doExecuteJinBi() {
            logger.info("${target}进入了[禁闭]状态")
            val g = r.game!!
            InvalidSkill.deal(target)
            target.skills += CannotPlayCard(forbidAllCard = true)
            g.players.send {
                skillJinBiBToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                }
            }
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            e.whoseTurn.getSkillUseCount(SkillId.JIN_BI) == 0 || return false
            val player = e.whoseTurn.game!!.players.filter { p ->
                p!!.alive && p.isEnemy(e.whoseTurn)
            }.shuffled().maxByOrNull {
                it!!.cards.count { c -> c.type in listOf(Jie_Huo, Wu_Dao, Diao_Bao) }
            } ?: return false
            GameExecutor.post(e.whoseTurn.game!!, {
                skill.executeProtocol(e.whoseTurn.game!!, e.whoseTurn, skillJinBiATos {
                    targetPlayerId = e.whoseTurn.getAlternativeLocation(player.location)
                })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}