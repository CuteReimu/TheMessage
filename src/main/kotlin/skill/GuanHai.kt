package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type.Jie_Huo
import com.fengsheng.protos.Common.card_type.Wu_Dao
import com.fengsheng.protos.Role.skill_guan_hai_toc
import org.apache.log4j.Logger

/**
 * 池镜海技能【观海】：你使用【截获】或【误导】时，在结算前先查看待收情报。
 */
class GuanHai : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.GUAN_HAI

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnUseCard ?: return null
        val fsm2 = fsm.currentFsm as? FightPhaseIdle ?: return null
        fsm.player === askWhom || return null
        askWhom.findSkill(skillId) != null || return null
        fsm.cardType == Jie_Huo || fsm.cardType == Wu_Dao || return null
        askWhom.getSkillUseCount(skillId) == 0 || return null
        log.info("${askWhom}发动了[观海]")
        askWhom.addSkillUseCount(skillId)
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_guan_hai_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                builder.card = fsm2.messageCard.toPbCard()
                p.send(builder.build())
            }
        }
        val oldResolveFunc = fsm.resolveFunc
        return ResolveResult(fsm.copy(resolveFunc = { valid ->
            askWhom.resetSkillUseCount(skillId)
            oldResolveFunc(valid)
        }), true)
    }

    companion object {
        private val log = Logger.getLogger(GuanHai::class.java)
    }
}