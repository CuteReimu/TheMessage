package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Role

class SkillJiangHuLingBTos : AbstractProtoHandler<Role.skill_jiang_hu_ling_b_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_jiang_hu_ling_b_tos) {
        r.game!!.tryContinueResolveProtocol(r, pb)
    }
}
