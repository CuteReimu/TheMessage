package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class SkillJianRenBTos : AbstractProtoHandler<Role.skill_jian_ren_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_jian_ren_b_tos) {
        val skill = r.findSkill(SkillId.JIAN_REN)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
