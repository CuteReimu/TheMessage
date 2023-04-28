package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_xin_si_chao_tos : AbstractProtoHandler<Role.skill_xin_si_chao_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_xin_si_chao_tos) {
        val skill = r.findSkill(SkillId.XIN_SI_CHAO) as? ActiveSkill
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_xin_si_chao_tos::class.java)
    }
}