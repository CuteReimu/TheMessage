package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Role
import com.fengsheng.skill.*
import org.apache.log4j.Logger

/**
 * SP李宁玉技能【诱导】：你使用【误导】后，摸一张牌。
 */
class YouDao : AbstractSkill(), TriggeredSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.YOU_DAO
    }

    override fun execute(g: Game): ResolveResult? {
        if (g.fsm !is OnUseCard || fsm.askWhom !== fsm.player || fsm.askWhom.findSkill<Skill>(skillId) == null) return null
        if (fsm.cardType != Common.card_type.Wu_Dao) return null
        if (fsm.askWhom.getSkillUseCount(skillId) > 0) return null
        fsm.askWhom.addSkillUseCount(skillId)
        val r: Player = fsm.askWhom
        YouDao.Companion.log.info(r.toString() + "发动了[诱导]")
        for (p in g.players) {
            (p as? HumanPlayer)?.send(
                Role.skill_you_dao_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location())).build()
            )
        }
        r.draw(1)
        val oldResolveFunc: Fsm = fsm.resolveFunc
        fsm.resolveFunc = Fsm {
            r.resetSkillUseCount(skillId)
            oldResolveFunc.resolve()
        }
        return ResolveResult(fsm, true)
    }

    companion object {
        private val log = Logger.getLogger(YouDao::class.java)
    }
}