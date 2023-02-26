package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng.select_role_tos

class select_role_tos : AbstractProtoHandler<select_role_tos>() {
    override fun handle0(r: HumanPlayer, pb: select_role_tos) {
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}