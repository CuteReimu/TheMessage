package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_chi_zi_zhi_xin_a_tos : AbstractProtoHandler<Role.skill_chi_zi_zhi_xin_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_chi_zi_zhi_xin_a_tos) {
        val skill = r.findSkill(SkillId.CHI_ZI_ZHI_XIN)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
