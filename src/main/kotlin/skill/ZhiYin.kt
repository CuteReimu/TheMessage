package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.ReceivePhaseSkill
import com.fengsheng.protos.Common.color.Blue
import com.fengsheng.protos.Common.color.Red
import com.fengsheng.protos.Role.skill_zhi_yin_toc
import org.apache.log4j.Logger

/**
 * 程小蝶技能【知音】：你接收红色或蓝色情报后，你和传出者各摸一张牌
 */
class ZhiYin : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.ZHI_YIN

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? ReceivePhaseSkill ?: return null
        askWhom === fsm.inFrontOfWhom || return null
        fsm.inFrontOfWhom.findSkill(skillId) != null || return null
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
            val players = arrayListOf(fsm.inFrontOfWhom)
            if (fsm.sender.alive) players.add(fsm.sender)
            g.sortedFrom(players, fsm.whoseTurn.location).forEach { it.draw(1) }
        }
        return null
    }

    companion object {
        private val log = Logger.getLogger(ZhiYin::class.java)
    }
}