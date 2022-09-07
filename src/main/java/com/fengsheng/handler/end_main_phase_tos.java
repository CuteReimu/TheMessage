package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.phase.SendPhaseStart;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class end_main_phase_tos extends AbstractProtoHandler<Fengsheng.end_main_phase_tos> {
    private static final Logger log = Logger.getLogger(end_main_phase_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.end_main_phase_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (!(r.getGame().getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("不是你的回合的出牌阶段");
            return;
        }
        r.incrSeq();
        r.getGame().resolve(new SendPhaseStart(fsm.player()));
    }
}
