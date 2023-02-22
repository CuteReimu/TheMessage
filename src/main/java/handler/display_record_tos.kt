package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng

com.google.protobuf.GeneratedMessageV3
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Logger
class display_record_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null || player.isLoadingRecord) {
            log.error("player is already in a room")
            return
        }
        val pb = message as Fengsheng.display_record_tos
        player.loadRecord(pb.version, pb.recordId)
    }

    companion object {
        private val log = Logger.getLogger(display_record_tos::class.java)
    }
}