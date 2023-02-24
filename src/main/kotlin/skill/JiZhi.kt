package com.fengsheng.skill

import com.fengsheng.Gameimport

com.fengsheng.GameExecutorimport com.fengsheng.HumanPlayerimport com.fengsheng.Playerimport com.fengsheng.phase.FightPhaseIdleimport com.fengsheng.phase.WaitForChengQingimport com.fengsheng.protos.Roleimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.concurrent.*
/**
 * 顾小梦技能【集智】：一名角色濒死时，或争夺阶段，你可以翻开此角色牌，然后摸四张牌。
 */
class JiZhi : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.JI_ZHI
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if ((g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn)
            && (g.fsm !is WaitForChengQing || r !== fsm2.askWhom)
        ) {
            log.error("现在不是发动[急智]的时机")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("角色面朝上时不能发动[急智]")
            return
        }
        val pb = message as Role.skill_ji_zhi_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        log.info(r.toString() + "发动了[急智]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_ji_zhi_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location())).build()
            )
        }
        g.playerSetRoleFaceUp(r, true)
        r.draw(4)
        if (g.fsm is FightPhaseIdle) fsm.whoseFightTurn = fsm.inFrontOfWhom
        g.continueResolve()
    }

    companion object {
        private val log = Logger.getLogger(JiZhi::class.java)
        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            if (e.whoseFightTurn.isRoleFaceUp) return false
            GameExecutor.Companion.post(e.whoseFightTurn.game, Runnable {
                skill.executeProtocol(
                    e.whoseFightTurn.game, e.whoseFightTurn, Role.skill_ji_zhi_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}