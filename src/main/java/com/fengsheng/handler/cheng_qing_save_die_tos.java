package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.card.Card;
import com.fengsheng.phase.WaitForChengQing;
import com.fengsheng.phase.WaitNextForChengQing;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class cheng_qing_save_die_tos extends AbstractProtoHandler<Fengsheng.cheng_qing_save_die_tos> {
    private static final Logger log = Logger.getLogger(cheng_qing_save_die_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.cheng_qing_save_die_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (!(r.getGame().getFsm() instanceof WaitForChengQing fsm) || r != fsm.askWhom) {
            log.error("现在不是使用澄清的时机");
            return;
        }
        if (!pb.getUse()) {
            r.incrSeq();
            r.getGame().resolve(new WaitNextForChengQing(fsm));
            return;
        }
        Card card = r.findCard(pb.getCardId());
        if (card == null) {
            log.error("没有这张牌");
            return;
        }
        if (card.getType() != Common.card_type.Cheng_Qing) {
            log.error("这张牌不是澄清，而是" + card);
            return;
        }
        Player target = fsm.whoDie;
        if (card.canUse(r.getGame(), r, target, pb.getTargetCardId())) {
            r.incrSeq();
            card.execute(r.getGame(), r, target, pb.getTargetCardId());
        }
    }
}
