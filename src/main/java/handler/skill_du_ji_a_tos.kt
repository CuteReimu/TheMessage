package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId

com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.*
class skill_du_ji_a_tos : AbstractProtoHandler<Role.skill_du_ji_a_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_du_ji_a_tos) {
        val skill = r.findSkill<ActiveSkill>(SkillId.DU_JI)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        if (HashSet(pb.targetPlayerIdsList).size != pb.targetPlayerIdsCount) {
            log.error("选择的角色重复" + Arrays.toString(pb.targetPlayerIdsList.toTypedArray()))
            return
        }
        skill.executeProtocol(r.game, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_du_ji_a_tos::class.java)
    }
}