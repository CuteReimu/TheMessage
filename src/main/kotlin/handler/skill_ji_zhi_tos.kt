package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId

com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
class skill_ji_zhi_tos : AbstractProtoHandler<Role.skill_ji_zhi_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_ji_zhi_tos) {
        val skill = r.findSkill<ActiveSkill>(SkillId.JI_ZHI)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_ji_zhi_tos::class.java)
    }
}