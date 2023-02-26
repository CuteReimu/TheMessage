package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.FightPhaseNext
import com.fengsheng.protos.Fengsheng
import org.apache.log4j.Logger

class end_fight_phase_tos : AbstractProtoHandler<Fengsheng.end_fight_phase_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.end_fight_phase_tos) {
        if (!r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            return
        }
        val fsm = r.game!!.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            log.error("时机不对")
            return
        }
        r.incrSeq()
        r.game!!.resolve(FightPhaseNext(fsm))
    }

    companion object {
        private val log = Logger.getLogger(end_fight_phase_tos::class.java)
    }
}