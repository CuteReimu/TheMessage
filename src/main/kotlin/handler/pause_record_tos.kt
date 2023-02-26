package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger

class pause_record_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null) {
            log.error("player is already in a room")
            return
        }
        if (!player.isLoadingRecord) {
            log.error("player is not loading record")
            return
        }
        val pb = message as Fengsheng.pause_record_tos
        player.pauseRecord(pb.pause)
        player.send(Fengsheng.pause_record_toc.newBuilder().setPause(pb.pause).build())
    }

    companion object {
        private val log = Logger.getLogger(pause_record_tos::class.java)
    }
}