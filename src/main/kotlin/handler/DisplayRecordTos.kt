package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger

class DisplayRecordTos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessage) {
        if (player.game != null || player.isLoadingRecord) {
            logger.error("player is already in a room")
            player.sendErrorMessage("你已经在房间里了")
            return
        }
        val pb = message as Fengsheng.display_record_tos
        player.loadRecord(pb.version, pb.recordId, pb.skipCount)
    }
}
