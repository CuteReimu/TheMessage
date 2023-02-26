package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_dui_zheng_xia_yao_c_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_dui_zheng_xia_yao_c_tos : AbstractProtoHandler<skill_dui_zheng_xia_yao_c_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_dui_zheng_xia_yao_c_tos) {
        val skill = r.findSkill(SkillId.DUI_ZHENG_XIA_YAO)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_dui_zheng_xia_yao_c_tos::class.java)
    }
}