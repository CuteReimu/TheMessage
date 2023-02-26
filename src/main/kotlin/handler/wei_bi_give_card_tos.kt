package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng.wei_bi_give_card_tos
import org.apache.log4j.Logger

class wei_bi_give_card_tos : AbstractProtoHandler<wei_bi_give_card_tos>() {
    override fun handle0(r: HumanPlayer, pb: wei_bi_give_card_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(wei_bi_give_card_tos::class.java)
    }
}