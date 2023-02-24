package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class execute_shi_tan_tos : AbstractProtoHandler<Fengsheng.execute_shi_tan_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.execute_shi_tan_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        r.game.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(execute_shi_tan_tos::class.java)
    }
}