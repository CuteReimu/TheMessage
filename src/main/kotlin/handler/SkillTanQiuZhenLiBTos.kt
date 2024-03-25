package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role

class SkillTanQiuZhenLiBTos : AbstractProtoHandler<Role.skill_tan_qiu_zhen_li_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_tan_qiu_zhen_li_b_tos) {
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
