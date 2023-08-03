package com.fengsheng.handler

import com.fengsheng.Config
import com.fengsheng.HumanPlayer
import com.fengsheng.Statistics
import com.fengsheng.protos.Errcode.error_code.client_version_not_match
import com.fengsheng.protos.Errcode.error_code_toc
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
        // 客户端版本号不对，get_record_list_tos
        if (pb.version < Config.ClientVersion) {
            val builder = error_code_toc.newBuilder()
            builder.code = client_version_not_match
            builder.addIntParams(Config.ClientVersion.toLong())
            player.send(builder.build())
            return
        }
        Statistics.displayRecordList(player)
    }

    companion object {
        private val log = Logger.getLogger(get_record_list_tos::class.java)
    }
}