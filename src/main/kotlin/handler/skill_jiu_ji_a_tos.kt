package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_jiu_ji_a_tos : AbstractProtoHandler<Role.skill_jiu_ji_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_jiu_ji_a_tos) {
        val skill = r.findSkill(SkillId.JIU_JI)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
