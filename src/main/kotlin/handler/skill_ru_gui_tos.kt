package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_ru_gui_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_ru_gui_tos : AbstractProtoHandler<skill_ru_gui_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_ru_gui_tos) {
        val skill = r.findSkill(SkillId.RU_GUI)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_ru_gui_tos::class.java)
    }
}