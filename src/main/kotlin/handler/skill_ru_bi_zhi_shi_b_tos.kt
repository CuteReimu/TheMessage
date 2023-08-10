package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_ru_bi_zhi_shi_b_tos : AbstractProtoHandler<Role.skill_ru_bi_zhi_shi_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_ru_bi_zhi_shi_a_tos) {
        val skill = r.findSkill(SkillId.RU_BI_ZHI_SHI)
        if (skill == null) {
            log.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_ru_bi_zhi_shi_b_tos::class.java)
    }
}