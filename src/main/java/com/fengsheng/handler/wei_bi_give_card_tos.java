package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class wei_bi_give_card_tos extends AbstractProtoHandler<Fengsheng.wei_bi_give_card_tos> {
    private static final Logger log = Logger.getLogger(wei_bi_give_card_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.wei_bi_give_card_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
