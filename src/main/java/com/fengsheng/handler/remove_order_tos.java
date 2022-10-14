package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Statistics;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class remove_order_tos extends AbstractProtoHandler<Fengsheng.remove_order_tos> {
    private static final Logger log = Logger.getLogger(remove_order_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Fengsheng.remove_order_tos pb) {
        if (Statistics.getInstance().removeOrder(r.getDevice(), pb.getId())) {
            r.send(Fengsheng.remove_order_toc.getDefaultInstance());
        } else {
            log.error("你没有这个预约");
        }
    }
}
