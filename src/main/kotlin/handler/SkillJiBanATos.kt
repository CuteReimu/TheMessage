package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class SkillJiBanATos : AbstractProtoHandler<Role.skill_ji_ban_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_ji_ban_a_tos) {
        val skill = r.findSkill(SkillId.JI_BAN) as? ActiveSkill
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }
}
