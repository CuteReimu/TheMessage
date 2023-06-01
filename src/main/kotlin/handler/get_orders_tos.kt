package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng

class get_orders_tos : AbstractProtoHandler<Fengsheng.get_orders_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.get_orders_tos) {
        r.send(Fengsheng.get_orders_toc.getDefaultInstance())
    }
}