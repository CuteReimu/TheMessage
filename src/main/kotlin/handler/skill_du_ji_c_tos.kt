package com.fengsheng.handler

import com.fengsheng.HumanPlayer

com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimer

class skill_du_ji_c_tos : AbstractProtoHandler<Role.skill_du_ji_c_tos?>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_du_ji_c_tos?) {
        r.game.tryContinueResolveProtocol(r, pb)
    }
}