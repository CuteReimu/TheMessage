package com.fengsheng.handler;

import com.fengsheng.Config;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Statistics;
import com.fengsheng.protos.Errcode;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

public class get_record_list_tos implements ProtoHandler {
    private static final Logger log = Logger.getLogger(get_record_list_tos.class);

    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        if (player.getGame() != null || player.isLoadingRecord()) {
            log.error("player is already in a room");
            return;
        }
        var pb = (Fengsheng.get_record_list_tos) message;
        // 客户端版本号不对，get_record_list_tos
        if (pb.getVersion() < Config.ClientVersion) {
            player.send(Errcode.error_code_toc.newBuilder()
                    .setCode(Errcode.error_code.client_version_not_match)
                    .addIntParams(Config.ClientVersion).build());
            player.getChannel().close();
            return;
        }
        Statistics.getInstance().displayRecordList(player);
    }
}
