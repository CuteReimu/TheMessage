package com.fengsheng.handler

import com.fengsheng.Config
import com.fengsheng.HumanPlayer
import com.fengsheng.Statistics
import com.fengsheng.protos.Fengsheng
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger

class get_record_list_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null || player.isLoadingRecord) {
            log.error("player is already in a room")
            player.sendErrorMessage("你已经在房间里了")
            return
        }
        val pb = message as Fengsheng.get_record_list_tos
        // 客户端版本号不对，直接返回错误信息
        if (pb.version < Config.ClientVersion) {
            player.sendErrorMessage("客户端版本号过低，请更新客户端")
            return
        }
        Statistics.displayRecordList(player)
    }

    companion object {
        private val log = Logger.getLogger(get_record_list_tos::class.java)
    }
}