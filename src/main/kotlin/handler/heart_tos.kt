package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng

com.google.protobuf.GeneratedMessageV3
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimer

class heart_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        val buf = Fengsheng.heart_toc.newBuilder().setOnlineCount(Game.Companion.deviceCache.size).build().toByteArray()
        player.send("heart_toc", buf, true)
    }
}