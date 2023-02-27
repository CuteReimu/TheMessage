package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_yi_hua_jie_mu_tos
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_yi_hua_jie_mu_tos : AbstractProtoHandler<skill_yi_hua_jie_mu_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_yi_hua_jie_mu_tos) {
        val skill = r.findSkill(SkillId.YI_HUA_JIE_MU) as ActiveSkill?
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_yi_hua_jie_mu_tos::class.java)
    }
}