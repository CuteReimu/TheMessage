package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Fengsheng
import org.apache.logging.log4j.kotlin.logger

class UseChengQingTos : AbstractProtoHandler<Fengsheng.use_cheng_qing_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.use_cheng_qing_tos) {
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
        if (card.type != card_type.Cheng_Qing) {
            logger.error("这张牌不是澄清，而是$card")
            r.sendErrorMessage("这张牌不是澄清，而是$card")
            return
        }
        if (pb.playerId < 0 || pb.playerId >= r.game!!.players.size) {
            logger.error("目标错误: ${pb.playerId}")
            r.sendErrorMessage("目标错误: ${pb.playerId}")
            return
        }
        val target = r.game!!.players[r.getAbstractLocation(pb.playerId)]!!
        if (card.canUse(r.game!!, r, target, pb.targetCardId)) {
            r.incrSeq()
            card.execute(r.game!!, r, target, pb.targetCardId)
        }
    }
}
