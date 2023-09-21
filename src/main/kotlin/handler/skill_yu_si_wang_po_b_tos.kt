package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import org.apache.log4j.Logger

class skill_yu_si_wang_po_b_tos : AbstractProtoHandler<Role.skill_yu_si_wang_po_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_yu_si_wang_po_b_tos) {
        if (HashSet(pb.cardIdsList).size != pb.cardIdsCount) {
            log.error("卡牌重复${pb.cardIdsList.toTypedArray().contentToString()}")
            r.sendErrorMessage("卡牌重复${pb.cardIdsList.toTypedArray().contentToString()}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_yu_si_wang_po_b_tos::class.java)
    }
}