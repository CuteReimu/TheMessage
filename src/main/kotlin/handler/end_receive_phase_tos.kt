package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng.end_receive_phase_tos
import org.apache.log4j.Logger

class end_receive_phase_tos : AbstractProtoHandler<end_receive_phase_tos>() {
    override fun handle0(r: HumanPlayer, pb: end_receive_phase_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(end_receive_phase_tos::class.java)
    }
}