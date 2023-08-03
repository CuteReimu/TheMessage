package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_dui_zheng_xia_yao_b_tos : AbstractProtoHandler<Role.skill_dui_zheng_xia_yao_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_dui_zheng_xia_yao_b_tos) {
        val skill = r.findSkill(SkillId.DUI_ZHENG_XIA_YAO)
        if (skill == null) {
            log.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        if (HashSet(pb.cardIdsList).size != pb.cardIdsCount) {
            log.error("卡牌重复${pb.cardIdsList.toTypedArray()}")
            r.sendErrorMessage("卡牌重复${pb.cardIdsList.toTypedArray()}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_dui_zheng_xia_yao_b_tos::class.java)
    }
}