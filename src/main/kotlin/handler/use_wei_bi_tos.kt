package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng.use_wei_bi_tos
import org.apache.log4j.Logger

class use_wei_bi_tos : AbstractProtoHandler<use_wei_bi_tos>() {
    override fun handle0(r: HumanPlayer, pb: use_wei_bi_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            return
        }
        if (card.type != card_type.Wei_Bi) {
            log.error("这张牌不是威逼，而是$card")
            return
        }
        if (pb.playerId < 0 || pb.playerId >= r.game!!.players.size) {
            log.error("目标错误: ${pb.playerId}")
            return
        }
        val target = r.game!!.players[r.getAbstractLocation(pb.playerId)]!!
        if (card.canUse(r.game!!, r, target, pb.wantType)) {
            r.incrSeq()
            card.execute(r.game!!, r, target, pb.wantType)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_wei_bi_tos::class.java)
    }
}