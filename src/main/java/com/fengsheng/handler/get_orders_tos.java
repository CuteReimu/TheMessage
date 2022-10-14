package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.Statistics;
import com.fengsheng.protos.Fengsheng;

public class get_orders_tos extends AbstractProtoHandler<Fengsheng.get_orders_tos> {
    @Override
    protected void handle0(HumanPlayer r, Fengsheng.get_orders_tos pb) {
        r.send(Fengsheng.get_orders_toc.newBuilder().addAllOrders(Statistics.getInstance().getOrders(r.getDevice())).build());
    }
}
