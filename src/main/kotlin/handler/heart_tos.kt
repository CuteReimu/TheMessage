package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng.heart_toc
import com.google.protobuf.GeneratedMessageV3

class heart_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        val c = Game.onlineCount
        val builder = heart_toc.newBuilder().setOnlineCount(c)
        player.send("heart_toc", builder.build().toByteArray(), true)
    }
}