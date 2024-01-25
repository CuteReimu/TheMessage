package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common
import com.fengsheng.protos.Fengsheng
import org.apache.logging.log4j.kotlin.logger

class use_feng_yun_bian_huan_tos : AbstractProtoHandler<Fengsheng.use_feng_yun_bian_huan_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_feng_yun_bian_huan_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (card.type != Common.card_type.Feng_Yun_Bian_Huan) {
            logger.error("这张牌不是风云变幻，而是$card")
            r.sendErrorMessage("这张牌不是风云变幻，而是$card")
            return
        }
        if (card.canUse(r.game!!, r)) {
            r.incrSeq()
            card.execute(r.game!!, r)
        }
    }
}