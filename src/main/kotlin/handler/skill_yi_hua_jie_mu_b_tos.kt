package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_yi_hua_jie_mu_b_tos : AbstractProtoHandler<Role.skill_yi_hua_jie_mu_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_yi_hua_jie_mu_b_tos) {
        val skill = r.findSkill(SkillId.YI_HUA_JIE_MU)
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