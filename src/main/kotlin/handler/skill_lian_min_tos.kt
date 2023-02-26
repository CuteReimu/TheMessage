package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_lian_min_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_lian_min_tos : AbstractProtoHandler<skill_lian_min_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_lian_min_tos) {
        val skill = r.findSkill(SkillId.LIAN_MIN)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_lian_min_tos::class.java)
    }
}