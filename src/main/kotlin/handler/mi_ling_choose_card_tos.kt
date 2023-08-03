package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class mi_ling_choose_card_tos : AbstractProtoHandler<Fengsheng.mi_ling_choose_card_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.mi_ling_choose_card_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(mi_ling_choose_card_tos::class.java)
    }
}