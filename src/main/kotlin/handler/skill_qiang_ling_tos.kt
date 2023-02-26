package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role.skill_qiang_ling_tos
import com.fengsheng.skill.SkillId
import org.apache.log4j.Logger
import java.util.*

class skill_qiang_ling_tos : AbstractProtoHandler<skill_qiang_ling_tos>() {
    override fun handle0(r: HumanPlayer, pb: skill_qiang_ling_tos) {
        val skill = r.findSkill(SkillId.QIANG_LING)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        if (pb.typesCount != 0 && EnumSet.copyOf(pb.typesList).size != pb.typesCount) {
            log.error("宣言的卡牌类型重复${pb.typesValueList.toTypedArray().contentToString()}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_qiang_ling_tos::class.java)
    }
}