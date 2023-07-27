package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseReceiverSkill
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Role.skill_zhi_yin_toc
import org.apache.log4j.Logger

/**
 * 程小蝶技能【知音】：你接收红色或蓝色情报后，你和传出者各摸一张牌
 */
class ZhiYin : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.ZHI_YIN

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseReceiverSkill ?: return null
        fsm.inFrontOfWhom.findSkill(skillId) != null || return null
        fsm.inFrontOfWhom.alive || return null
        fsm.inFrontOfWhom.getSkillUseCount(skillId) == 0 || return null
        val colors = fsm.messageCard.colors
        Red in colors || Blue in colors || return null
        fsm.inFrontOfWhom.addSkillUseCount(skillId)
        log.info("${fsm.inFrontOfWhom}发动了[知音]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_zhi_yin_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(fsm.inFrontOfWhom.location)
                p.send(builder.build())
            }
        }
        if (fsm.inFrontOfWhom === fsm.sender) {
            fsm.inFrontOfWhom.draw(2)
        } else {
            fsm.inFrontOfWhom.draw(1)
            if (fsm.sender.alive) fsm.sender.draw(1)
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(ZhiYin::class.java)
    }
}