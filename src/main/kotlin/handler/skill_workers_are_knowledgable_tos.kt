package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger

class skill_workers_are_knowledgable_tos : AbstractProtoHandler<Role.skill_workers_are_knowledgable_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_workers_are_knowledgable_tos) {
        val skill = r.findSkill(SkillId.WORKERS_ARE_KNOWLEDGABLE)
        if (skill == null) {
            log.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        if (HashSet(pb.targetPlayerIdList).size != pb.targetPlayerIdCount) {
            log.error("选择的目标重复${pb.targetPlayerIdList.toTypedArray().contentToString()}")
            r.sendErrorMessage("卡牌重复${pb.targetPlayerIdList.toTypedArray().contentToString()}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_workers_are_knowledgable_tos::class.java)
    }
}