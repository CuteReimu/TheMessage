package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_you_di_shen_ru_tos : AbstractProtoHandler<Role.skill_you_di_shen_ru_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_you_di_shen_ru_tos) {
        val skill = r.findSkill(SkillId.YOU_DI_SHEN_RU) as? ActiveSkill
        if (skill == null) {
            log.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_you_di_shen_ru_tos::class.java)
    }
}