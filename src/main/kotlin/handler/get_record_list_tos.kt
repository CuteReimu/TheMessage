package com.fengsheng.handler

import com.fengsheng.*
import com.fengsheng.protos.Errcode
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Fengsheng
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger

class get_record_list_tos : ProtoHandler {
    override fun handle(player: HumanPlayer, message: GeneratedMessageV3) {
        if (player.game != null || player.isLoadingRecord) {
            log.error("player is already in a room")
            return
        }
        val pb = message as Fengsheng.get_record_list_tos
        // 客户端版本号不对，get_record_list_tos
        if (pb.version < Config.ClientVersion) {
            player.send(
                error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.client_version_not_match)
                    .addIntParams(Config.ClientVersion.toLong()).build()
            )
            player.channel.close()
            return
        }
        Statistics.Companion.getInstance().displayRecordList(player)
    }

    companion object {
        private val log = Logger.getLogger(get_record_list_tos::class.java)
    }
}