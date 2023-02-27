package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.skill_yi_hua_jie_mu_toc
import com.fengsheng.protos.Role.skill_yi_hua_jie_mu_tos
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit

/**
 * 韩梅技能【移花接木】：争夺阶段，你可以翻开此角色牌，然后从一名角色的情报区选择一张情报，将其置入另一名角色的情报区，若如此做会让其收集三张或更多同色情报，则改为将该情报加入你的手牌。
 */
class YiHuaJieMu : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.YI_HUA_JIE_MU

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            log.error("[移花接木]的使用时机不对")
            return
        }
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[移花接木]")
            return
        }
        val pb = message as skill_yi_hua_jie_mu_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        if (pb.fromPlayerId < 0 || pb.fromPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val fromPlayer = r.game!!.players[r.getAbstractLocation(pb.fromPlayerId)]!!
        if (!fromPlayer.alive) {
            log.error("目标已死亡")
            return
        }
        if (pb.toPlayerId < 0 || pb.toPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val toPlayer = r.game!!.players[r.getAbstractLocation(pb.toPlayerId)]!!
        if (!toPlayer.alive) {
            log.error("目标已死亡")
            return
        }
        if (pb.fromPlayerId == pb.toPlayerId) {
            log.error("选择的两个目标不能相同")
            return
        }
        val card = fromPlayer.findMessageCard(pb.cardId)
        if (card == null) {
            log.error("没有这张卡")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        log.info("${r}发动了[移花接木]")
        fromPlayer.deleteMessageCard(card.id)
        val joinIntoHand = toPlayer.checkThreeSameMessageCard(card)
        if (joinIntoHand) {
            r.cards.add(card)
            log.info("${fromPlayer}面前的${card}加入了${r}的手牌")
        } else {
            toPlayer.messageCards.add(card)
            log.info("${fromPlayer}面前的${card}加入了${toPlayer}的情报区")
        }
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_yi_hua_jie_mu_toc.newBuilder()
                builder.cardId = card.id
                builder.joinIntoHand = joinIntoHand
                builder.playerId = p.getAlternativeLocation(r.location)
                builder.fromPlayerId = p.getAlternativeLocation(fromPlayer.location)
                builder.toPlayerId = p.getAlternativeLocation(toPlayer.location)
                p.send(builder.build())
            }
        }
        g.resolve(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom))
    }

    companion object {
        private val log = Logger.getLogger(YiHuaJieMu::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val p = e.whoseFightTurn
            if (p.roleFaceUp) return false
            val blackCard = p.messageCards.find {
                it.colors.size == 1 && it.colors.first() == color.Black
            } ?: return false
            val target = e.whoseTurn
            if (p === target || !target.alive) return false
            if (p.identity == color.Black || p.identity != target.identity) {
                val black = target.messageCards.count { it.colors.contains(color.Black) }
                if (black < 2) {
                    GameExecutor.post(e.whoseFightTurn.game!!, {
                        val builder = skill_yi_hua_jie_mu_tos.newBuilder()
                        builder.cardId = blackCard.id
                        builder.fromPlayerId = 0
                        builder.toPlayerId = p.getAlternativeLocation(target.location)
                        skill.executeProtocol(e.whoseFightTurn.game!!, e.whoseFightTurn, builder.build())
                    }, 2, TimeUnit.SECONDS)
                    return true
                }
            }
            return false
        }
    }
}