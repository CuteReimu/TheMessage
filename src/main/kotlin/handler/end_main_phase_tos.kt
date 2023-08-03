package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Fengsheng

import org.apache.log4j.Logger

class end_main_phase_tos : AbstractProtoHandler<Fengsheng.end_main_phase_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.end_main_phase_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = r.game!!.fsm as? MainPhaseIdle
        if (r !== fsm?.player) {
            log.error("不是你的回合的出牌阶段")
            r.sendErrorMessage("不是你的回合的出牌阶段")
            return
        }
        r.incrSeq()
        r.game!!.resolve(SendPhaseStart(fsm.player))
    }

    companion object {
        private val log = Logger.getLogger(end_main_phase_tos::class.java)
    }
}