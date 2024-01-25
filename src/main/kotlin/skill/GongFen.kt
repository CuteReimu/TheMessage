package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.count
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Role.skill_gong_fen_toc
import com.fengsheng.protos.Role.skill_gong_fen_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * SP程小蝶技能【共焚】：争夺阶段，你可以翻开此角色牌，然后从你开始，逆时针每名玩家翻开牌堆顶的一张牌并置入自己的情报区，若翻开的是红色或蓝色牌，则改为加入你的手牌。
 */
class GongFen : ActiveSkill {
    override val skillId = SkillId.GONG_FEN

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[共焚]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[共焚]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[共焚]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[共焚]")
            return
        }
        val pb = message as skill_gong_fen_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        logger.info("${r}发动了[共焚]")
        val q = ArrayDeque<Player>(g.players.size)
        for (i in r.location..<(r.location + g.players.size)) {
            val player = g.players[i % g.players.size]!!
            if (player.alive) q.addLast(player)
        }
        g.resolve(executeGongFen(fsm, q))
    }

    private data class executeGongFen(
        val fsm: FightPhaseIdle,
        val q: ArrayDeque<Player>,
        val asMessage: Boolean = false
    ) : Fsm {
        override fun resolve(): ResolveResult? {
            val target = q.removeFirstOrNull()
            if (target == null) {
                if (asMessage) fsm.whoseTurn.game!!.addEvent(AddMessageCardEvent(fsm.whoseTurn))
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            val r = fsm.whoseFightTurn
            val g = r.game!!
            val cards = g.deck.draw(1)
            if (cards.isEmpty()) return ResolveResult(fsm, true)
            val card = cards[0]
            val asMessage = card.isPureBlack()
            if (asMessage) {
                logger.info("${card}被置入${target}的情报区")
                target.messageCards.add(card)
            } else {
                logger.info("${card}加入${r}的手牌")
                r.cards.add(card)
            }
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_gong_fen_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.card = card.toPbCard()
                    p.send(builder.build())
                }
            }
            GameExecutor.post(g, {
                if (asMessage && !this.asMessage) g.resolve(copy(asMessage = true)) else g.continueResolve()
            }, 1, TimeUnit.SECONDS)
            return null
        }
    }

    companion object {
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.roleFaceUp) return false
            if (player.game!!.players.all { it!!.messageCards.count(Black) < 2 }) return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skill_gong_fen_tos.getDefaultInstance())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}