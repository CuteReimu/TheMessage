package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.phase.WaitNextForChengQing
import com.fengsheng.protos.Common
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class cheng_qing_save_die_tos : AbstractProtoHandler<Fengsheng.cheng_qing_save_die_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.cheng_qing_save_die_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        val fsm = r.game!!.fsm as? WaitForChengQing
        if (r !== fsm?.askWhom) {
            log.error("现在不是使用澄清的时机")
            return
        }
        if (!pb.use) {
            r.incrSeq()
            r.game!!.resolve(WaitNextForChengQing(fsm))
            return
        }
        val card = r.findCard(pb.cardId)
        if (card == null) {
            log.error("没有这张牌")
            return
        }
        if (card.type != Common.card_type.Cheng_Qing) {
            log.error("这张牌不是澄清，而是$card")
            return
        }
        val target: Player = fsm.whoDie
        if (card.canUse(r.game!!, r, target, pb.targetCardId)) {
            r.incrSeq()
            card.execute(r.game!!, r, target, pb.targetCardId)
        }
    }

    companion object {
        private val log = Logger.getLogger(cheng_qing_save_die_tos::class.java)
    }
}