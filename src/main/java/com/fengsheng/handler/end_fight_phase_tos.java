package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.phase.FightPhaseNext;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class end_fight_phase_tos extends AbstractProtoHandler<Fengsheng.end_fight_phase_tos> {
    private static final Logger log = Logger.getLogger(end_fight_phase_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.end_fight_phase_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (!(r.getGame().getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("时机不对");
            return;
        }
        r.incrSeq();
        r.getGame().resolve(new FightPhaseNext(fsm));
    }
}
