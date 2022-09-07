package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.phase.MessageMoveNext;
import com.fengsheng.phase.OnChooseReceiveCard;
import com.fengsheng.phase.SendPhaseIdle;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class choose_whether_receive_tos extends AbstractProtoHandler<Fengsheng.choose_whether_receive_tos> {
    private static final Logger log = Logger.getLogger(choose_whether_receive_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.choose_whether_receive_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (!(r.getGame().getFsm() instanceof SendPhaseIdle fsm) || r != fsm.inFrontOfWhom) {
            log.error("不是选择是否接收情报的时机");
            return;
        }
        if (pb.getReceive()) {
            r.incrSeq();
            r.getGame().resolve(new OnChooseReceiveCard(fsm.whoseTurn, fsm.messageCard, fsm.inFrontOfWhom, fsm.isMessageCardFaceUp));
        } else {
            if (r == fsm.whoseTurn) {
                log.error("传出者必须接收");
                return;
            }
            boolean locked = false;
            for (Player a : fsm.lockedPlayers) {
                if (r == a) {
                    locked = true;
                    break;
                }
            }
            if (locked) {
                log.error("被锁定，必须接收");
                return;
            }
            r.incrSeq();
            r.getGame().resolve(new MessageMoveNext(fsm));
        }
    }
}
