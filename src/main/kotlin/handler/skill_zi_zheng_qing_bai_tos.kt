package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_zi_zheng_qing_bai_tos : AbstractProtoHandler<Role.skill_zi_zheng_qing_bai_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_zi_zheng_qing_bai_tos) {
        val skill = r.findSkill(SkillId.ZI_ZHENG_QING_BAI) as? ActiveSkill
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }
}