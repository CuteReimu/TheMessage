package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Role.skill_shun_shi_er_wei_toc
import org.apache.log4j.Logger

/**
 * 中年小九技能【顺势而为】：你使用【截获】或者成为【截获】的目标时，可以翻回背面。
 */
class ShunShiErWei : InitialSkill, TriggeredSkill {
    override val skillId = SkillId.SHUN_SHI_ER_WEI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        askWhom.alive || return null
        fsm.cardType == Jie_Huo || return null
        askWhom === fsm.player || askWhom === (fsm.currentFsm as? FightPhaseIdle)?.inFrontOfWhom || return null
        askWhom.roleFaceUp || return null
        askWhom.addSkillUseCount(skillId)
        log.info("${askWhom}发动了[顺势而为]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_shun_shi_er_wei_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        g.playerSetRoleFaceUp(askWhom, false)
        return null
    }

    companion object {
        private val log = Logger.getLogger(JiangJiJiuJi::class.java)
    }
}