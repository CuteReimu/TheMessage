package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Statistics;
import com.fengsheng.protos.Fengsheng;

public class add_order_tos extends AbstractProtoHandler<Fengsheng.add_order_tos> {
    @Override
    protected void handle0(HumanPlayer r, Fengsheng.add_order_tos pb) {
        Statistics.getInstance().addOrder(r.getDevice(), r.getPlayerName(), pb.getTime());
        r.send(Fengsheng.add_order_toc.getDefaultInstance());
    }
}
