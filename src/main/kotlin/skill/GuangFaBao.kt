package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 小九技能【广发报】：争夺阶段，你可以翻开此角色牌，然后摸三张牌，并且你可以将你的任意张手牌置入任意名角色的情报区。你不能通过此技能让任何角色收集三张或更多的同色情报。
 */
class GuangFaBao : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.GUANG_FA_BAO

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[广发报]的时机")
            return
        }
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[广发报]")
            return
        }
        val pb = message as skill_guang_fa_bao_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        log.info("${r}发动了[广发报]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                skill_guang_fa_bao_a_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location)).build()
            )
        }
        r.draw(3)
        g.resolve(executeGuangFaBao(fsm, r))
    }

    private data class executeGuangFaBao(val fsm: FightPhaseIdle, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_wait_for_guang_fa_bao_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_guang_fa_bao_b_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    for (p in g.players) {
                        val cardIds = ArrayList<Int>()
                        val cards = ArrayList<Card>()
                        for (card in r.cards) {
                            cards.add(card)
                            if (p!!.checkThreeSameMessageCard(*cards.toTypedArray())) cards.remove(card)
                            else cardIds.add(card.id)
                        }
                        if (cardIds.isNotEmpty()) {
                            val builder = skill_guang_fa_bao_b_tos.newBuilder()
                            builder.enable = true
                            builder.targetPlayerId = r.getAlternativeLocation(p!!.location)
                            builder.addAllCardIds(cardIds)
                            g.tryContinueResolveProtocol(r, builder.build())
                            return@post
                        }
                    }
                    g.tryContinueResolveProtocol(r, skill_guang_fa_bao_b_tos.newBuilder().setEnable(false).build())
                }, 1, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is skill_guang_fa_bao_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_guang_fa_bao_b_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                return null
            }
            if (message.cardIdsCount == 0) {
                log.error("enable为true时至少要发一张牌")
                return null
            }
            val cards = Array(message.cardIdsCount) {
                val card = r.findCard(message.getCardIds(it))
                if (card == null) {
                    log.error("没有这张卡")
                    return null
                }
                card
            }
            if (target.checkThreeSameMessageCard(*cards)) {
                log.error("你不能通过此技能让任何角色收集三张或更多的同色情报")
                return null
            }
            r.incrSeq()
            log.info("${r}将${cards.contentToString()}置于${target}的情报区")
            r.cards.removeAll(cards.toSet())
            target.messageCards.addAll(cards)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_guang_fa_bao_b_toc.newBuilder().setEnable(true)
                    for (card in cards) builder.addCards(card.toPbCard())
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            if (r.cards.isNotEmpty()) return ResolveResult(this, true)
            return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
        }

        companion object {
            private val log = Logger.getLogger(executeGuangFaBao::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(GuangFaBao::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.roleFaceUp) return false
            if (player.cards.size < 5) return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skill_guang_fa_bao_a_tos.newBuilder().build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}