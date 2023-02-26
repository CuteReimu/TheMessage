package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.Statistics
import com.fengsheng.protos.Fengsheng.get_orders_toc
import com.fengsheng.protos.Fengsheng.get_orders_tos

class get_orders_tos : AbstractProtoHandler<get_orders_tos>() {
    override fun handle0(r: HumanPlayer, pb: get_orders_tos) {
        r.send(get_orders_toc.newBuilder().addAllOrders(Statistics.getOrders(r.device!!)).build())
    }
}