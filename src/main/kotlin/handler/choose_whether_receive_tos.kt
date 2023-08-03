package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.MessageMoveNext
import com.fengsheng.phase.OnChooseReceiveCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class choose_whether_receive_tos : AbstractProtoHandler<Fengsheng.choose_whether_receive_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.choose_whether_receive_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = r.game!!.fsm as? SendPhaseIdle
        if (r !== fsm?.inFrontOfWhom) {
            log.error("不是选择是否接收情报的时机")
            r.sendErrorMessage("不是选择是否接收情报的时机")
            return
        }
        if (pb.receive) {
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
            if (r === fsm.sender) {
                log.error("传出者必须接收")
                r.sendErrorMessage("传出者必须接收")
                return
            }
            var locked = false
            for (a in fsm.lockedPlayers) {
                if (r === a) {
                    locked = true
                    break
                }
            }
            if (locked) {
                log.error("被锁定，必须接收")
                r.sendErrorMessage("被锁定，必须接收")
                return
            }
            r.incrSeq()
            r.game!!.resolve(MessageMoveNext(fsm))
        }
    }

    companion object {
        private val log = Logger.getLogger(choose_whether_receive_tos::class.java)
    }
}