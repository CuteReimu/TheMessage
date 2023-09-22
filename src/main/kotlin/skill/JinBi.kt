package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnGiveCard
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 王田香技能【禁闭】：出牌阶段限一次，你可以指定一名角色，除非其交给你两张手牌，否则其本回合不能使用手牌，且所有角色技能无效。
 */
class JinBi : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.JIN_BI

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
            log.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[禁闭]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[禁闭]一回合只能发动一次")
            return
        }
        val pb = message as skill_jin_bi_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            log.error("不能以自己为目标")
            (r as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info("${r}对${target}发动了[禁闭]")
        g.resolve(executeJinBi(r, target))
    }

    private data class executeJinBi(val r: Player, val target: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (target.cards.size < 2) {
                doExecuteJinBi()
                return ResolveResult(MainPhaseIdle(r), true)
            }
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_bi_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === target) {
                        val seq = p.seq
                        builder.seq = seq
                        GameExecutor.post(p.game!!, {
                            if (p.checkSeq(seq)) {
                                val builder2 = skill_jin_bi_b_tos.newBuilder()
                                builder2.seq = seq
                                p.game!!.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer)
                GameExecutor.post(target.game!!, {
                    target.game!!.tryContinueResolveProtocol(target, skill_jin_bi_b_tos.getDefaultInstance())
                }, 2, TimeUnit.SECONDS)
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== target) {
                log.error("你不是被禁闭的目标")
                (player as? HumanPlayer)?.sendErrorMessage("你不是被禁闭的目标")
                return null
            }
            if (message !is skill_jin_bi_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = target.game!!
            if (target is HumanPlayer && !target.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${target.seq}, actual Seq: ${message.seq}")
                target.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.cardIdsCount == 0) {
                target.incrSeq()
                doExecuteJinBi()
                return ResolveResult(MainPhaseIdle(r), true)
            } else if (message.cardIdsCount != 2) {
                log.error("给的牌数量不对：${message.cardIdsCount}")
                (player as? HumanPlayer)?.sendErrorMessage("给的牌数量不对：${message.cardIdsCount}")
                return null
            }
            val cards = Array(2) {
                val card = target.findCard(message.getCardIds(it))
                if (card == null) {
                    log.error("没有这张牌")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return null
                }
                card
            }
            target.incrSeq()
            target.cards.removeAll(cards.toSet())
            r.cards.addAll(cards)
            log.info("${target}给了${r}${cards.contentToString()}")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_bi_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) {
                        for (card in cards) builder.addCards(card.toPbCard())
                    } else {
                        builder.unknownCardCount = 2
                    }
                    p.send(builder.build())
                }
            }
            return ResolveResult(OnGiveCard(r, target, r, MainPhaseIdle(r)), true)
        }

        private fun doExecuteJinBi() {
            log.info("${target}进入了[禁闭]状态")
            val g = r.game!!
            g.jinBiPlayer = target
            InvalidSkill.deal(target)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jin_bi_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
        }

        companion object {
            private val log = Logger.getLogger(executeJinBi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(JinBi::class.java)
        fun resetJinBi(game: Game) {
            game.jinBiPlayer = null
        }

        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.player.getSkillUseCount(SkillId.JIN_BI) > 0) return false
            val players = e.player.game!!.players.filter { p -> p!!.alive && p.isEnemy(e.player) }
            val player = players.randomOrNull() ?: return false
            GameExecutor.post(e.player.game!!, {
                val builder = skill_jin_bi_a_tos.newBuilder()
                builder.targetPlayerId = e.player.getAlternativeLocation(player.location)
                skill.executeProtocol(e.player.game!!, e.player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}