package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_guang_fa_bao_a_tos
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_guang_fa_bao_a_tos : AbstractProtoHandler<skill_guang_fa_bao_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_guang_fa_bao_a_tos) {
        val skill = r.findSkill(SkillId.GUANG_FA_BAO) as? ActiveSkill
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_guang_fa_bao_a_tos::class.java)
    }
}