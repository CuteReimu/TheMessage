package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_cheng_zhi_tos : AbstractProtoHandler<Role.skill_cheng_zhi_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_cheng_zhi_tos) {
        val skill = r.findSkill(SkillId.CHENG_ZHI)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
    }
}