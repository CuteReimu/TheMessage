package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_jin_shen_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_jin_shen_tos : AbstractProtoHandler<skill_jin_shen_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_jin_shen_tos) {
        val skill = r.findSkill(SkillId.JIN_SHEN)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_jin_shen_tos::class.java)
    }
}