package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_jian_di_feng_xing_c_tos : AbstractProtoHandler<Role.skill_jian_di_feng_xing_c_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_jian_di_feng_xing_c_tos) {
        val skill = r.findSkill(SkillId.JIAN_DI_FENG_XING)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
