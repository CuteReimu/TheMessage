package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role

class skill_han_hou_lao_shi_tos : AbstractProtoHandler<Role.skill_han_hou_lao_shi_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_han_hou_lao_shi_tos) {
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}