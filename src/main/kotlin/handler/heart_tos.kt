package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.heartToc
import com.google.protobuf.GeneratedMessageV3

class heart_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        val c = Game.onlineCount
        player.send("heart_toc", heartToc { onlineCount = c }.toByteArray(), true)
    }
}