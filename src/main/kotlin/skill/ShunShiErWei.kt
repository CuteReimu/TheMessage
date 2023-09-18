package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common
import com.fengsheng.protos.Role.skill_shun_shi_er_wei_toc
import org.apache.log4j.Logger

/**
 * 中年小九技能【顺势而为】：你使用【截获】或者成为【截获】的目标时，可以翻回背面。
 */
class ShunShiErWei : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.SHUN_SHI_ER_WEI

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        fsm.askWhom.alive || return null
        fsm.askWhom.findSkill(skillId) != null || return null
        fsm.cardType == Common.card_type.Jie_Huo || return null
        fsm.askWhom === fsm.player || fsm.askWhom === (fsm.currentFsm as? FightPhaseIdle)?.inFrontOfWhom || return null
        fsm.player.roleFaceUp || return null
        fsm.askWhom.addSkillUseCount(skillId)
        log.info("${fsm.askWhom}发动了[顺势而为]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_shun_shi_er_wei_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(fsm.askWhom.location)
                p.send(builder.build())
            }
        }
        g.playerSetRoleFaceUp(fsm.askWhom, false)
        return null
    }

    companion object {
        private val log = Logger.getLogger(JiangJiJiuJi::class.java)
    }
}