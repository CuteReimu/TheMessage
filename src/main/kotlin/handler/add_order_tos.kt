package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.Statistics
import com.fengsheng.protos.Fengsheng

class add_order_tos : AbstractProtoHandler<Fengsheng.add_order_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.add_order_tos) {
        Statistics.addOrder(r.device!!, r.playerName, pb.time.toLong())
        r.send(Fengsheng.add_order_toc.getDefaultInstance())
    }
}