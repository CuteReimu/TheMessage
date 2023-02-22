package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.ReceivePhaseReceiverSkillimport

com.fengsheng.protos.Common.color com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
/**
 * 程小蝶技能【知音】：你接收红色或蓝色情报后，你和传出者各摸一张牌
 */
class ZhiYin : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.ZHI_YIN
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is ReceivePhaseReceiverSkill || fsm.inFrontOfWhom.findSkill<Skill>(skillId) == null || !fsm.inFrontOfWhom.isAlive()) return null
        if (fsm.inFrontOfWhom.getSkillUseCount(skillId) > 0) return null
        val colors: List<color> = fsm.messageCard.getColors()
        if (!colors.contains(color.Red) && !colors.contains(color.Blue)) return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        log.info(fsm.inFrontOfWhom.toString() + "发动了[知音]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_zhi_yin_toc.newBuilder().setPlayerId(p.getAlternativeLocation(fsm.inFrontOfWhom.location()))
                    .build()
            )
        }
        if (fsm.inFrontOfWhom === fsm.whoseTurn) {
            fsm.inFrontOfWhom.draw(2)
        } else {
            fsm.inFrontOfWhom.draw(1)
            if (fsm.whoseTurn.isAlive()) fsm.whoseTurn.draw(1)
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(ZhiYin::class.java)
    }
}