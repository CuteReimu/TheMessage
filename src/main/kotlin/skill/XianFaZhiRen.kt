package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.card.count
import com.fengsheng.card.filter
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.HasReceiveOrder
import com.fengsheng.phase.OnAddMessageCard
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 钱敏技能【先发制人】：一张牌因角色技能置入情报区后，或争夺阶段，你可以翻开此角色，然后弃置一名角色情报区的一张情报，并令一张角色牌本回合所有技能无效，若其是面朝下的隐藏角色牌，你可以将其翻开。
 */
class XianFaZhiRen : AbstractSkill(), ActiveSkill, TriggeredSkill {
    override val skillId = SkillId.XIAN_FA_ZHI_REN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnAddMessageCard ?: return null
        val r = fsm.resolvingWhom
        if (r.findSkill(skillId) == null || !r.alive) return null
        if (r.roleFaceUp) return null
        if (!fsm.bySkill) return null
        if (g.players.all { it!!.messageCards.isEmpty() }) return null
        return ResolveResult(executeXianFaZhiRenA(fsm), true)
    }

    private data class executeXianFaZhiRenA(val fsm: OnAddMessageCard) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val r = fsm.resolvingWhom
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = wait_for_skill_xian_fa_zhi_ren_a_toc.newBuilder()
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2 = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_xian_fa_zhi_ren_a_tos.newBuilder()
                                builder2.enable = false
                                g.tryContinueResolveProtocol(p, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder2 = skill_xian_fa_zhi_ren_a_tos.newBuilder()
                    val targetAndCard = g.players.flatMap {
                        if (it!!.isPartnerOrSelf(r)) {
                            if (it.messageCards.count(Black) >= 3)
                                return@flatMap it.messageCards.filter(Black).map { card -> PlayerAndCard(it, card) }
                        } else if (it.messageCards.count(Red) == 3) {
                            return@flatMap it.messageCards.filter(Red).map { card -> PlayerAndCard(it, card) }
                        } else if (it.messageCards.count(Blue) == 3) {
                            return@flatMap it.messageCards.filter(Blue).map { card -> PlayerAndCard(it, card) }
                        }
                        emptyList()
                    }.randomOrNull()
                    builder2.enable = targetAndCard != null
                    if (targetAndCard != null) {
                        builder2.targetPlayerId = r.getAlternativeLocation(targetAndCard.player.location)
                        builder2.cardId = targetAndCard.card.id
                    }
                    g.tryContinueResolveProtocol(r, builder2.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== fsm.resolvingWhom) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_xian_fa_zhi_ren_a_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                player.incrSeq()
                return ResolveResult(fsm.resolveNext(), true)
            }
            val target = player.game!!.players[player.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.deleteMessageCard(message.cardId)
            if (card == null) {
                log.error("没有这张情报")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张情报")
                return null
            }
            player.incrSeq()
            player.game!!.playerSetRoleFaceUp(player, true)
            log.error("${player}发动了[先发制人]，弃掉了${target}面前的$card")
            player.game!!.deck.discard(card)
            (fsm.afterResolve as? HasReceiveOrder)?.receiveOrder?.removePlayerIfNotHaveThreeBlack(target)
            for (p in player.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = skill_xian_fa_zhi_ren_a_toc.newBuilder()
                    builder.enable = message.enable
                    builder.playerId = p.getAlternativeLocation(player.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.cardId = card.id
                    builder.waitingSecond = SKILL_TIMEOUT
                    if (p === player) builder.seq = p.seq
                    p.send(builder.build())
                }
            }
            return ResolveResult(executeXianFaZhiRenB(fsm, player, target), true)
        }

        companion object {
            private val log = Logger.getLogger(executeXianFaZhiRenA::class.java)
        }
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null || r !== fsm.whoseFightTurn) {
            log.error("不是你发技能的时机")
            (r as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
            return
        }
        if (r.roleFaceUp) {
            log.error("你面朝上，不能发动技能")
            (r as? HumanPlayer)?.sendErrorMessage("你面朝上，不能发动技能")
            return
        }
        message as skill_xian_fa_zhi_ren_a_tos
        if (r is HumanPlayer && !r.checkSeq(message.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (!message.enable) {
            log.error("错误的协议")
            (r as? HumanPlayer)?.sendErrorMessage("错误的协议")
            return
        }
        if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
            log.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        val target = r.game!!.players[r.getAbstractLocation(message.targetPlayerId)]!!
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        val card = target.deleteMessageCard(message.cardId)
        if (card == null) {
            log.error("没有这张情报")
            (r as? HumanPlayer)?.sendErrorMessage("没有这张情报")
            return
        }
        r.incrSeq()
        r.game!!.playerSetRoleFaceUp(r, true)
        log.error("${r}发动了[先发制人]，弃掉了${target}面前的$card")
        r.game!!.deck.discard(card)
        for (p in r.game!!.players) {
            if (p is HumanPlayer) {
                val builder = skill_xian_fa_zhi_ren_a_toc.newBuilder()
                builder.enable = message.enable
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                builder.cardId = card.id
                builder.waitingSecond = SKILL_TIMEOUT
                if (p === r) builder.seq = p.seq
                p.send(builder.build())
            }
        }
        r.game!!.resolve(executeXianFaZhiRenB(fsm, r, target))
    }

    private data class executeXianFaZhiRenB(val fsm: Fsm, val r: Player, val defaultTarget: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            if (r is HumanPlayer) {
                val seq = r.seq
                r.timeout = GameExecutor.post(r.game!!, {
                    if (r.checkSeq(seq)) {
                        val builder = skill_xian_fa_zhi_ren_b_tos.newBuilder()
                        builder.targetPlayerId = r.getAlternativeLocation(defaultTarget.location)
                        builder.faceUp = false
                        builder.seq = seq
                        r.game!!.tryContinueResolveProtocol(r, builder.build())
                    }
                }, r.getWaitSeconds(SKILL_TIMEOUT + 2).toLong(), TimeUnit.SECONDS)
            } else {
                GameExecutor.post(r.game!!, {
                    val builder = skill_xian_fa_zhi_ren_b_tos.newBuilder()
                    builder.targetPlayerId = r.getAlternativeLocation(defaultTarget.location)
                    builder.faceUp = !defaultTarget.roleFaceUp && defaultTarget.isEnemy(r)
                    r.game!!.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_xian_fa_zhi_ren_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            if (message.faceUp && target.roleFaceUp) {
                log.error("目标本来就是面朝上的")
                (player as? HumanPlayer)?.sendErrorMessage("目标本来就是面朝上的")
                return null
            }
            r.incrSeq()
            log.info("${target}的技能被无效了")
            InvalidSkill.deal(target)
            if (message.faceUp) g.playerSetRoleFaceUp(target, true)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_xian_fa_zhi_ren_b_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.faceUp = message.faceUp
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm, true)
        }

        companion object {
            private val log = Logger.getLogger(executeXianFaZhiRenB::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(XianFaZhiRen::class.java)
        private const val SKILL_TIMEOUT = 15

        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.roleFaceUp) return false
            val g = player.game!!
            val target = e.inFrontOfWhom
            if (!target.alive) return false
            val card = target.messageCards.run {
                if (target.isPartnerOrSelf(player)) {
                    if (count(Black) >= 2 && Black in e.messageCard.colors)
                        return@run filter(Black)
                } else if (count(Red) == 2 && Red in e.messageCard.colors) {
                    return@run target.messageCards.filter(Red)
                } else if (count(Blue) == 2 && Blue in e.messageCard.colors) {
                    return@run target.messageCards.filter(Blue)
                }
                emptyList()
            }.randomOrNull() ?: return false
            GameExecutor.post(g, {
                val builder2 = skill_xian_fa_zhi_ren_a_tos.newBuilder()
                builder2.enable = true
                builder2.targetPlayerId = player.getAlternativeLocation(target.location)
                builder2.cardId = card.id
                skill.executeProtocol(g, player, builder2.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}