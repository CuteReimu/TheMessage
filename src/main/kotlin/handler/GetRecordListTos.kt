package com.fengsheng.handler

import com.fengsheng.Config
import com.fengsheng.HumanPlayer
import com.fengsheng.Statistics
import com.fengsheng.protos.Fengsheng
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger

class GetRecordListTos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessage) {
        if (player.game != null || player.isLoadingRecord) {
            logger.error("player is already in a room")
            player.sendErrorMessage("你已经在房间里了")
            return
        }
        val pb = message as Fengsheng.get_record_list_tos
        // 客户端版本号不对，直接返回错误信息
        if (pb.version < Config.ClientVersion.get()) {
            player.sendErrorMessage("客户端版本号过低，请重新下载最新客户端")
            return
        }
        Statistics.displayRecordList(player)
    }
}
