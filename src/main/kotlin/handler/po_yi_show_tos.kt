package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng

import org.apache.logging.log4j.kotlin.logger

class po_yi_show_tos : AbstractProtoHandler<Fengsheng.po_yi_show_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.po_yi_show_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
    }
}