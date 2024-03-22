package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger

class skill_tao_qu_a_tos : AbstractProtoHandler<Role.skill_tao_qu_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_tao_qu_a_tos) {
        val skill = r.findSkill(SkillId.TAO_QU) as? ActiveSkill
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        if (HashSet(pb.cardIdsList).size != pb.cardIdsCount) {
            logger.error("卡牌重复${pb.cardIdsList.joinToString()}")
            r.sendErrorMessage("卡牌重复${pb.cardIdsList.joinToString()}")
            return
        }
        skill.executeProtocol(r.game!!, r, pb)
    }
}
