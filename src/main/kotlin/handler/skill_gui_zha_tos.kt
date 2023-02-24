package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.Skill
import com.fengsheng.skill.SkillId

com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
class skill_gui_zha_tos : AbstractProtoHandler<Role.skill_gui_zha_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_gui_zha_tos) {
        val skill = r.findSkill<Skill>(SkillId.GUI_ZHA)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        (skill as ActiveSkill).executeProtocol(r.game, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_gui_zha_tos::class.java)
    }
}