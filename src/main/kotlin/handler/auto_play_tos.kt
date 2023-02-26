package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng

class auto_play_tos : AbstractProtoHandler<Fengsheng.auto_play_tos>() {
    public override fun handle0(r: HumanPlayer, pb: Fengsheng.auto_play_tos) {
        r.setAutoPlay(pb.enable)
    }
}