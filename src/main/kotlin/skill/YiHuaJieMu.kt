package com.fengsheng.skill

import com.fengsheng.Gameimport

com.fengsheng.GameExecutorimport com.fengsheng.HumanPlayerimport com.fengsheng.Playerimport com.fengsheng.card.*import com.fengsheng.phase.FightPhaseIdleimport

com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 韩梅技能【移花接木】：争夺阶段，你可以翻开此角色牌，然后从一名角色的情报区选择一张情报，将其置入另一名角色的情报区，若如此做会让其收集三张或更多同色情报，则改为将该情报加入你的手牌。
 */
class YiHuaJieMu : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.YI_HUA_JIE_MU
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("[移花接木]的使用时机不对")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[移花接木]")
            return
        }
        val pb = message as Role.skill_yi_hua_jie_mu_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        if (pb.fromPlayerId < 0 || pb.fromPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val fromPlayer = r.game.players[r.getAbstractLocation(pb.fromPlayerId)]
        if (!fromPlayer.isAlive) {
            log.error("目标已死亡")
            return
        }
        if (pb.toPlayerId < 0 || pb.toPlayerId >= g.players.size) {
            log.error("目标错误")
            return
        }
        val toPlayer = r.game.players[r.getAbstractLocation(pb.toPlayerId)]
        if (!toPlayer.isAlive) {
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
        log.info(r.toString() + "发动了[移花接木]")
        fromPlayer.deleteMessageCard(card.id)
        var joinIntoHand = false
        if (toPlayer.checkThreeSameMessageCard(card)) {
            joinIntoHand = true
            r.addCard(card)
            log.info(fromPlayer.toString() + "面前的" + card + "加入了" + r + "的手牌")
        } else {
            toPlayer.addMessageCard(card)
            log.info(fromPlayer.toString() + "面前的" + card + "加入了" + toPlayer + "的情报区")
        }
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_yi_hua_jie_mu_toc.newBuilder().setCardId(card.id).setJoinIntoHand(joinIntoHand)
                    .setPlayerId(p.getAlternativeLocation(r.location()))
                    .setFromPlayerId(p.getAlternativeLocation(fromPlayer.location()))
                    .setToPlayerId(p.getAlternativeLocation(toPlayer.location())).build()
            )
        }
        fsm.whoseFightTurn = fsm.inFrontOfWhom
        g.continueResolve()
    }

    companion object {
        private val log = Logger.getLogger(YiHuaJieMu::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val p = e.whoseFightTurn
            if (p.isRoleFaceUp) return false
            var blackCard: Card? = null
            for (card in p.messageCards.values) {
                if (card.colors.size == 1 && card.colors[0] == color.Black) {
                    blackCard = card
                    break
                }
            }
            if (blackCard == null) return false
            val targetCard: Card = blackCard
            val target = e.whoseTurn
            if (p === target || !target.isAlive) return false
            if (p.identity == color.Black || p.identity != target.identity) {
                var black = 0
                for (card in target.messageCards.values) {
                    if (card.colors.contains(color.Black)) black++
                }
                if (black < 2) {
                    GameExecutor.Companion.post(e.whoseFightTurn.game, Runnable {
                        skill.executeProtocol(
                            e.whoseFightTurn.game,
                            e.whoseFightTurn,
                            Role.skill_yi_hua_jie_mu_tos.newBuilder().setCardId(targetCard.id)
                                .setFromPlayerId(0).setToPlayerId(p.getAlternativeLocation(target.location())).build()
                        )
                    }, 2, TimeUnit.SECONDS)
                    return true
                }
            }
            return false
        }
    }
}