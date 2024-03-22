package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_zhuang_zhi_man_huai_tos : AbstractProtoHandler<Role.skill_zhuang_zhi_man_huai_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_zhuang_zhi_man_huai_tos) {
        val skill = r.findSkill(SkillId.ZHUANG_ZHI_MAN_HUAI)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
