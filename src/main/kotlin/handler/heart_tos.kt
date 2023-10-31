package com.fengsheng.handler

import com.fengsheng.Game
import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng.heart_toc
import com.google.protobuf.GeneratedMessageV3

class heart_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        val c = Game.GameCache.values.sumOf { it.players.size } + (player.game?.players?.count { it != null } ?: 0)
        val builder = heart_toc.newBuilder().setOnlineCount(c)
        player.send("heart_toc", builder.build().toByteArray(), true)
    }
}