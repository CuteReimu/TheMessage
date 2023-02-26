package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common
import com.fengsheng.protos.Fengsheng.use_feng_yun_bian_huan_tos
import org.apache.log4j.Logger

class use_feng_yun_bian_huan_tos : AbstractProtoHandler<use_feng_yun_bian_huan_tos>() {
    override fun handle0(r: HumanPlayer, pb: use_feng_yun_bian_huan_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            return
        }
        if (card.type != Common.card_type.Feng_Yun_Bian_Huan) {
            log.error("这张牌不是风云变幻，而是$card")
            return
        }
        if (card.canUse(r.game!!, r)) {
            r.incrSeq()
            card.execute(r.game!!, r)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_feng_yun_bian_huan_tos::class.java)
    }
}