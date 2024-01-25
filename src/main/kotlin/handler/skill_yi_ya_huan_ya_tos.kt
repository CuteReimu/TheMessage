package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_yi_ya_huan_ya_tos : AbstractProtoHandler<Role.skill_yi_ya_huan_ya_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_yi_ya_huan_ya_tos) {
        val skill = r.findSkill(SkillId.YI_YA_HUAN_YA)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}