package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class SkillCangShenJiaoTangCTos : AbstractProtoHandler<Role.skill_cang_shen_jiao_tang_c_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_cang_shen_jiao_tang_c_tos) {
        val skill = r.findSkill(SkillId.CANG_SHEN_JIAO_TANG)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
