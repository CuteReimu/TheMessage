package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.count
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnGiveCard
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 裴玲技能【交际】：出牌阶段限一次，你可以抽取一名角色的最多两张手牌。然后将等量手牌交给该角色。你每收集一张黑色情报，便可以少交一张牌。
 */
class JiaoJi : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.JIAO_JI

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? MainPhaseIdle
        if (r !== fsm?.player) {
            log.error("现在不是出牌阶段空闲时点")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            log.error("[交际]一回合只能发动一次")
            (r as? HumanPlayer)?.sendErrorMessage("[交际]一回合只能发动一次")
            return
        }
        val pb = message as skill_jiao_ji_a_tos
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
        if (target.cards.isEmpty()) {
            log.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        val cards: Array<Card>
        if (target.cards.size <= 2) {
            cards = target.cards.toTypedArray()
            target.cards.clear()
        } else {
            cards = Array(2) { target.deleteCard(target.cards.random().id)!! }
        }
        log.info("${r}对${target}发动了[交际]，抽取了${cards.contentToString()}")
        r.cards.addAll(cards)
        val black = r.messageCards.count(color.Black)
        val needReturnCount = (cards.size - black).coerceAtLeast(0)..cards.size
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_jiao_ji_a_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                if (p === r || p === target) {
                    for (card in cards) builder.addCards(card.toPbCard())
                } else {
                    builder.unknownCardCount = cards.size
                }
                builder.waitingSecond = Config.WaitSecond
                if (p === r) {
                    val seq = p.seq
                    builder.seq = seq
                    p.timeout = GameExecutor.post(g, {
                        if (p.checkSeq(seq)) {
                            val builder2 = skill_jiao_ji_b_tos.newBuilder()
                            for (c in r.cards) {
                                if (builder2.cardIdsCount >= needReturnCount.first) break
                                builder2.addCardIds(c.id)
                            }
                            builder2.seq = seq
                            g.tryContinueResolveProtocol(r, builder2.build())
                        }
                    }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                }
                p.send(builder.build())
            }
        }
        if (r is RobotPlayer) {
            GameExecutor.post(g, {
                val builder2 = skill_jiao_ji_b_tos.newBuilder()
                for (c in r.cards) {
                    if (builder2.cardIdsCount >= needReturnCount.first) break
                    builder2.addCardIds(c.id)
                }
                g.tryContinueResolveProtocol(r, builder2.build())
            }, 2, TimeUnit.SECONDS)
        }
        g.resolve(executeJiaoJi(fsm, target, needReturnCount))
    }

    private data class executeJiaoJi(val fsm: MainPhaseIdle, val target: Player, val needReturnCount: IntRange) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.player) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jiao_ji_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (message.cardIdsCount !in needReturnCount) {
                log.error("卡牌数量不正确，需要返还：${needReturnCount}，实际返还：${message.cardIdsCount}")
                (player as? HumanPlayer)?.sendErrorMessage("卡牌数量不正确，需要返还：${needReturnCount}，实际返还：${message.cardIdsCount}")
                return null
            }
            val r = fsm.player
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            val cards = Array(message.cardIdsCount) {
                val card = r.findCard(message.getCardIds(it))
                if (card == null) {
                    log.error("没有这张卡")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            r.incrSeq()
            log.info("${r}将${cards.contentToString()}还给$target")
            r.cards.removeAll(cards.toSet())
            target.cards.addAll(cards)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jiao_ji_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    if (p === r || p === target) {
                        for (card in cards) builder.addCards(card.toPbCard())
                    } else {
                        builder.unknownCardCount = cards.size
                    }
                    p.send(builder.build())
                }
            }
            val nextFsm = OnGiveCard(r, target, r, fsm)
            if (cards.isEmpty())
                return ResolveResult(nextFsm, true)
            return ResolveResult(OnGiveCard(r, r, target, nextFsm), true)
        }

        companion object {
            private val log = Logger.getLogger(executeJiaoJi::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(JiaoJi::class.java)
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.player
            player.getSkillUseCount(SkillId.JIAO_JI) == 0 || return false
            val players = player.game!!.players.filter { it !== player && it!!.alive && it.cards.isNotEmpty() }
            val target = players.filter { player.isEnemy(it!!) }.randomOrNull()
                ?: players.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                val builder = skill_jiao_ji_a_tos.newBuilder()
                builder.targetPlayerId = player.getAlternativeLocation(target.location)
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}