package com.fengsheng.skill

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
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

    override fun execute(g: Game, askWhom: Player): ResolveResult? {
        val fsm = g.fsm as? OnUseCard
        if (fsm == null || askWhom !== fsm.player || askWhom.findSkill(skillId) == null) return null
        if (fsm.cardType != card_type.Wu_Dao) return null
        if (askWhom.getSkillUseCount(skillId) > 0) return null
        askWhom.addSkillUseCount(skillId)
        log.info("${askWhom}发动了[诱导]")
        for (p in g.players) {
            if (p is HumanPlayer) {
                val builder = skill_you_dao_toc.newBuilder()
                builder.playerId = p.getAlternativeLocation(askWhom.location)
                p.send(builder.build())
            }
        }
        askWhom.draw(1)
        val oldResolveFunc = fsm.resolveFunc
        val newFsm = fsm.copy(resolveFunc = { valid ->
            askWhom.resetSkillUseCount(skillId)
            oldResolveFunc(valid)
        })
        return ResolveResult(newFsm, true)
    }

    companion object {
        private val log = Logger.getLogger(YouDao::class.java)
    }
}