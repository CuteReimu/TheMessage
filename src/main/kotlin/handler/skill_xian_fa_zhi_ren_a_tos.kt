package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.WaitingFsm
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import com.fengsheng.skill.XianFaZhiRen
import org.apache.logging.log4j.kotlin.logger

class skill_xian_fa_zhi_ren_a_tos : AbstractProtoHandler<Role.skill_xian_fa_zhi_ren_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_xian_fa_zhi_ren_a_tos) {
        val skill = r.findSkill(SkillId.XIAN_FA_ZHI_REN) as? XianFaZhiRen
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        if (r.game!!.fsm is WaitingFsm)
            r.game!!.tryContinueResolveProtocol(r, pb)
        else
            skill.executeProtocol(r.game!!, r, pb)
    }

    companion object {
    }
}