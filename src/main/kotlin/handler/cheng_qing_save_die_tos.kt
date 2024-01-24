package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.phase.WaitNextForChengQing
import com.fengsheng.protos.Common
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.RuBiZhiShi.excuteRuBiZhiShi
import org.apache.logging.log4j.kotlin.logger

class cheng_qing_save_die_tos : AbstractProtoHandler<Fengsheng.cheng_qing_save_die_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.cheng_qing_save_die_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (r.game!!.fsm is excuteRuBiZhiShi) {
            r.game!!.tryContinueResolveProtocol(r, pb)
            return
        }
        val fsm = r.game!!.fsm as? WaitForChengQing
        if (r !== fsm?.askWhom) {
            logger.error("现在不是使用澄清的时机")
            r.sendErrorMessage("现在不是使用澄清的时机")
            return
        }
        if (!pb.use) {
            r.incrSeq()
            r.game!!.resolve(WaitNextForChengQing(fsm))
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            logger.error("没有这张牌")
            r.sendErrorMessage("没有这张牌")
            return
        }
        if (card.type != Common.card_type.Cheng_Qing) {
            logger.error("这张牌不是澄清，而是$card")
            r.sendErrorMessage("这张牌不是澄清，而是$card")
            return
        }
        val target = fsm.whoDie
        if (card.canUse(r.game!!, r, target, pb.targetCardId)) {
            r.incrSeq()
            card.execute(r.game!!, r, target, pb.targetCardId)
        }
    }

    companion object {
    }
}