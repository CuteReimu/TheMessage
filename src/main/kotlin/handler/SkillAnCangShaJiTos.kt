package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class SkillAnCangShaJiTos : AbstractProtoHandler<Role.skill_an_cang_sha_ji_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_an_cang_sha_ji_tos) {
        val skill = r.findSkill(SkillId.AN_CANG_SHA_JI)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
