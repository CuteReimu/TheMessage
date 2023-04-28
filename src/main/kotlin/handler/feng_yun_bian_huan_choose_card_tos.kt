package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class feng_yun_bian_huan_choose_card_tos : AbstractProtoHandler<Fengsheng.feng_yun_bian_huan_choose_card_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.feng_yun_bian_huan_choose_card_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(feng_yun_bian_huan_choose_card_tos::class.java)
    }
}