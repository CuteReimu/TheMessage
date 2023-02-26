package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_gui_zha_tos
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_gui_zha_tos : AbstractProtoHandler<skill_gui_zha_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_gui_zha_tos) {
        val skill = r.findSkill(SkillId.GUI_ZHA) as? ActiveSkill
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_gui_zha_tos::class.java)
    }
}