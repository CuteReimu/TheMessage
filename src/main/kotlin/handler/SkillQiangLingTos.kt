package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import com.fengsheng.skill.SkillId
import org.apache.logging.log4j.kotlin.logger
import java.util.*

class SkillQiangLingTos : AbstractProtoHandler<Role.skill_qiang_ling_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_qiang_ling_tos) {
        val skill = r.findSkill(SkillId.QIANG_LING)
        if (skill == null) {
            logger.error("你没有这个技能")
            r.sendErrorMessage("你没有这个技能")
            return
        }
        if (pb.typesCount != 0 && EnumSet.copyOf(pb.typesList).size != pb.typesCount) {
            logger.error("宣言的卡牌类型重复${pb.typesValueList.joinToString()}")
            r.sendErrorMessage("宣言的卡牌类型重复${pb.typesValueList.joinToString()}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
