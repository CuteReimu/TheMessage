package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class po_yi_show_tos extends AbstractProtoHandler<Fengsheng.po_yi_show_tos> {
    private static final Logger log = Logger.getLogger(po_yi_show_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.po_yi_show_tos pb) {
        if (!r.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
