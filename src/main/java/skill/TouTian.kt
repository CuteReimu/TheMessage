package com.fengsheng.skill

import com.fengsheng.Gameimport

com.fengsheng.GameExecutorimport com.fengsheng.HumanPlayerimport com.fengsheng.Playerimport com.fengsheng.card.JieHuoimport com.fengsheng.phase.FightPhaseIdleimport com.fengsheng.protos.Common.colorimport com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 鄭文先技能【偷天】：争夺阶段你可以翻开此角色牌，然后视为你使用了一张【截获】。
 */
class TouTian : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.TOU_TIAN
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (!JieHuo.Companion.canUse(g, r)) return
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[偷天]")
            return
        }
        val pb = message as Role.skill_tou_tian_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        log.info(r.toString() + "发动了[偷天]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_tou_tian_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location())).build()
            )
        }
        JieHuo.Companion.execute(null, g, r)
    }

    companion object {
        private val log = Logger.getLogger(TouTian::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.whoseFightTurn.isRoleFaceUp) return false
            val player = e.whoseFightTurn
            val colors = e.messageCard.colors
            if (e.inFrontOfWhom === player || (e.isMessageCardFaceUp || player === e.whoseTurn) && colors.size == 1 && colors[0] == color.Black) return false
            if (ThreadLocalRandom.current().nextBoolean()) return false
            GameExecutor.Companion.post(e.whoseFightTurn.game, Runnable {
                skill.executeProtocol(
                    e.whoseFightTurn.game, e.whoseFightTurn, Role.skill_tou_tian_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}