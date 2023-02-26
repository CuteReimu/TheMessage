package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import com.google.protobuf.GeneratedMessageV3

class heart_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        val buf = Fengsheng.heart_toc.newBuilder().setOnlineCount(Game.deviceCache.size).build().toByteArray()
        player.send("heart_toc", buf, true)
    }
}