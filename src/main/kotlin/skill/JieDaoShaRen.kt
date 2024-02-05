package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 商玉技能【借刀杀人】：争夺阶段，你可以翻开此角色牌，然后抽取另一名角色的一张手牌并展示之。若展示的牌是：**黑色**，则你可以将其置入一名角色的情报区，并将你的角色牌翻至面朝下。**非黑色**，则你摸一张牌。
 */
class JieDaoShaRen : ActiveSkill {
    override val skillId = SkillId.JIE_DAO_SHA_REN

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[借刀杀人]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[借刀杀人]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[借刀杀人]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[借刀杀人]")
            return
        }
        val pb = message as skill_jie_dao_sha_ren_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= g.players.size) {
            logger.error("目标错误")
            (r as? HumanPlayer)?.sendErrorMessage("目标错误")
            return
        }
        if (pb.targetPlayerId == 0) {
            logger.error("不能以自己为目标")
            (r as? HumanPlayer)?.sendErrorMessage("不能以自己为目标")
            return
        }
        val target = g.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return
        }
        if (target.cards.isEmpty()) {
            logger.error("目标没有手牌")
            (r as? HumanPlayer)?.sendErrorMessage("目标没有手牌")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        val card = target.cards.random()
        g.resolve(executeJieDaoShaRen(fsm, r, target, card))
    }

    private data class executeJieDaoShaRen(val fsm: FightPhaseIdle, val r: Player, val target: Player, val card: Card) :
        WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            target.deleteCard(card.id)
            r.cards.add(card)
            g.addEvent(GiveCardEvent(fsm.whoseTurn, target, r))
            logger.info("${r}对${target}发动了[借刀杀人]，抽取了一张手牌$card")
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jie_dao_sha_ren_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.card = card.toPbCard()
                    if (card.isBlack()) {
                        builder.waitingSecond = Config.WaitSecond
                        if (p === r) {
                            val seq2 = p.seq
                            builder.seq = seq2
                            p.timeout = GameExecutor.post(g, {
                                if (p.checkSeq(seq2)) {
                                    val builder2 = skill_jie_dao_sha_ren_b_tos.newBuilder()
                                    builder2.enable = false
                                    builder2.seq = seq2
                                    g.tryContinueResolveProtocol(r, builder2.build())
                                }
                            }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                        }
                    }
                    p.send(builder.build())
                }
            }
            if (!card.isBlack()) {
                r.draw(1)
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    var value = Int.MIN_VALUE
                    var target2 = r
                    for (p in g.sortedFrom(g.players, r.location)) {
                        p.alive || continue
                        val result = r.calculateMessageCardValue(fsm.whoseTurn, p, card)
                        if (result > value) {
                            value = result
                            target2 = p
                        }
                    }
                    val builder = skill_jie_dao_sha_ren_b_tos.newBuilder()
                    builder.enable = true
                    builder.targetPlayerId = r.getAlternativeLocation(target2.location)
                    g.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_jie_dao_sha_ren_b_tos) {
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
                    if (p is HumanPlayer) {
                        val builder = skill_jie_dao_sha_ren_b_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            r.incrSeq()
            logger.info("${r}将${card}置于${target}的情报区")
            r.deleteCard(card.id)
            target.messageCards.add(card)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_jie_dao_sha_ren_b_toc.newBuilder()
                    builder.card = card.toPbCard()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.enable = true
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    p.send(builder.build())
                }
            }
            g.playerSetRoleFaceUp(r, false)
            g.addEvent(AddMessageCardEvent(fsm.whoseTurn))
            return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            !player.roleFaceUp || return false
            val target = player.game!!.players.filter {
                it !== player && it!!.alive && it.cards.isNotEmpty() &&
                        it.cards.count { card -> card.isBlack() }.let { blackCount ->
                            blackCount * 3 > it.cards.size * 2 // 黑牌占比大于 2/3
                        }
            }.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                val builder = skill_jie_dao_sha_ren_a_tos.newBuilder()
                builder.targetPlayerId = player.getAlternativeLocation(target.location)
                skill.executeProtocol(player.game!!, player, builder.build())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}