package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import org.apache.logging.log4j.kotlin.logger

class skill_jin_bi_b_tos : AbstractProtoHandler<Role.skill_jin_bi_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_jin_bi_b_tos) {
        if (HashSet(pb.cardIdsList).size != pb.cardIdsCount) {
            logger.error("卡牌重复${pb.cardIdsList.joinToString()}")
            r.sendErrorMessage("卡牌重复${pb.cardIdsList.joinToString()}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}