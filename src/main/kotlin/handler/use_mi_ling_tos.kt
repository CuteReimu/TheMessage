package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class use_mi_ling_tos : AbstractProtoHandler<Fengsheng.use_mi_ling_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_mi_ling_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (card.type != card_type.Mi_Ling) {
            log.error("这张牌不是密令，而是$card")
            r.sendErrorMessage("这张牌不是密令，而是$card")
            return
        }
        if (pb.targetPlayerId < 0 || pb.targetPlayerId >= r.game!!.players.size) {
            log.error("目标错误: ${pb.targetPlayerId}")
            r.sendErrorMessage("目标错误: ${pb.targetPlayerId}")
            return
        }
        if (pb.secret < 0 || pb.secret >= 3) {
            log.error("参数错误：${pb.secret}")
            r.sendErrorMessage("参数错误：${pb.secret}")
            return
        }
        val target = r.game!!.players[r.getAbstractLocation(pb.targetPlayerId)]!!
        if (card.canUse(r.game!!, r, target, pb.secret)) {
            r.incrSeq()
            card.execute(r.game!!, r, target, pb.secret)
        }
    }

    companion object {
        private val log = Logger.getLogger(use_mi_ling_tos::class.java)
    }
}