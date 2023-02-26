package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_jiang_hu_ling_b_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_jiang_hu_ling_b_tos : AbstractProtoHandler<skill_jiang_hu_ling_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_jiang_hu_ling_b_tos) {
        val skill = r.findSkill(SkillId.JIANG_HU_LING2)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_jiang_hu_ling_b_tos::class.java)
    }
}