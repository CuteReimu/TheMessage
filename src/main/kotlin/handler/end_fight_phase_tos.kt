package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.FightPhaseNext
import com.fengsheng.protos.Fengsheng
import org.apache.logging.log4j.kotlin.logger

class end_fight_phase_tos : AbstractProtoHandler<Fengsheng.end_fight_phase_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.end_fight_phase_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = r.game!!.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("时机不对")
            r.sendErrorMessage("时机不对")
            return
        }
        r.incrSeq()
        r.game!!.resolve(FightPhaseNext(fsm))
    }

    companion object {
    }
}