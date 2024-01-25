package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_hou_zi_qie_xin_tos : AbstractProtoHandler<Role.skill_hou_zi_qie_xin_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_hou_zi_qie_xin_tos) {
        val skill = r.findSkill(SkillId.HOU_ZI_QIE_XIN) as? ActiveSkill
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }
}