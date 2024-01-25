package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.MessageMoveNext
import com.fengsheng.phase.OnChooseReceiveCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.cannotReceiveMessage
import com.fengsheng.skill.mustReceiveMessage
import org.apache.logging.log4j.kotlin.logger

class choose_whether_receive_tos : AbstractProtoHandler<Fengsheng.choose_whether_receive_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.choose_whether_receive_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = r.game!!.fsm as? SendPhaseIdle
        if (r !== fsm?.inFrontOfWhom) {
            logger.error("不是选择是否接收情报的时机")
            r.sendErrorMessage("不是选择是否接收情报的时机")
            return
        }
        val mustReceive = fsm.mustReceiveMessage()
        if (pb.receive) {
            if (!mustReceive && fsm.cannotReceiveMessage()) {
                logger.error("不能选择接收情报")
                r.sendErrorMessage("不能选择接收情报")
                return
            }
            r.incrSeq()
            r.game!!.resolve(
                OnChooseReceiveCard(
                    fsm.whoseTurn,
                    fsm.sender,
                    fsm.messageCard,
                    fsm.inFrontOfWhom,
                    fsm.isMessageCardFaceUp
                )
            )
        } else {
            if (mustReceive) {
                logger.error("必须选择接收情报")
                r.sendErrorMessage("必须选择接收情报")
                return
            }
            r.incrSeq()
            r.game!!.resolve(MessageMoveNext(fsm))
        }
    }
}