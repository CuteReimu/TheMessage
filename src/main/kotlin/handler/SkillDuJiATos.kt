package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class SkillDuJiATos : AbstractProtoHandler<Role.skill_du_ji_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_du_ji_a_tos) {
        val skill = r.findSkill(SkillId.DU_JI) as? ActiveSkill
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        if (HashSet(pb.targetPlayerIdsList).size != pb.targetPlayerIdsCount) {
            logger.error("选择的角色重复${pb.targetPlayerIdsList.joinToString()}")
            r.sendErrorMessage("选择的角色重复${pb.targetPlayerIdsList.joinToString()}")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }
}
