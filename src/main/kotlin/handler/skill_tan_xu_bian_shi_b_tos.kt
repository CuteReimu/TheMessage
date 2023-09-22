package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role
import org.apache.log4j.Logger

class skill_tan_xu_bian_shi_b_tos : AbstractProtoHandler<Role.skill_tan_xu_bian_shi_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_tan_xu_bian_shi_b_tos) {
        r.game!!.tryContinueResolveProtocol(r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_tan_xu_bian_shi_b_tos::class.java)
    }
}