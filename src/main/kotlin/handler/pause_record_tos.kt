package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.pauseRecordToc
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger

class pause_record_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null) {
            logger.error("player is already in a room")
            player.sendErrorMessage("已经在房间里了")
            return
        }
        if (!player.isLoadingRecord) {
            logger.error("player is not loading record")
            player.sendErrorMessage("你没有在播放录像")
            return
        }
        val pb = message as Fengsheng.pause_record_tos
        player.pauseRecord(pb.pause)
        player.send(pauseRecordToc { pause = pb.pause })
    }
}