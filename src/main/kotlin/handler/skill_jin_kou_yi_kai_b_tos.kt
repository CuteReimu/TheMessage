package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_jin_kou_yi_kai_b_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_jin_kou_yi_kai_b_tos : AbstractProtoHandler<skill_jin_kou_yi_kai_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_jin_kou_yi_kai_b_tos) {
        val skill = r.findSkill(SkillId.JIN_KOU_YI_KAI)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_jin_kou_yi_kai_b_tos::class.java)
    }
}