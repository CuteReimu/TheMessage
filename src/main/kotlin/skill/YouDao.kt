package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.ResolveResult
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Role.skill_you_dao_toc
import org.apache.log4j.Logger

/**
 * SP李宁玉技能【诱导】：你使用【误导】后，摸一张牌。
 */
class YouDao : AbstractSkill(), TriggeredSkill {
    override val skillId = SkillId.YOU_DAO

    override fun execute(g: Game): ResolveResult? {
        val fsm = g.fsm as? OnUseCard
        if (fsm == null || fsm.askWhom !== fsm.player || fsm.askWhom.findSkill(skillId) == null) return null
        if (fsm.cardType != card_type.Wu_Dao) return null
        if (fsm.askWhom.getSkillUseCount(skillId) > 0) return null
        fsm.askWhom.addSkillUseCount(skillId)
        val r = fsm.askWhom
        log.info("${r}发动了[诱导]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_you_dao_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(r.location)
                p.send(builder.build())
            }
        }
        r.draw(1)
        val oldResolveFunc = fsm.resolveFunc
        val newFsm = fsm.copy(resolveFunc = { valid ->
            r.resetSkillUseCount(skillId)
            oldResolveFunc(valid)
        })
        return ResolveResult(newFsm, true)
    }

    companion object {
        private val log = Logger.getLogger(YouDao::class.java)
    }
}