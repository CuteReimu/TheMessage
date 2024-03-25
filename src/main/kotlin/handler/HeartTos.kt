package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.heartToc
import com.google.protobuf.GeneratedMessage

class HeartTos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessage) {
        val c = Game.onlineCount
        player.send("heart_toc", heartToc { onlineCount = c }.toByteArray(), true)
    }
}
