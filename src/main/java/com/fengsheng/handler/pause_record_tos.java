package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

public class pause_record_tos implements ProtoHandler {
    private static final Logger log = Logger.getLogger(pause_record_tos.class);

    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        if (player.getGame() != null) {
            log.error("player is already in a room");
            return;
        }
        if (!player.isLoadingRecord()) {
            log.error("player is not loading record");
            return;
        }
        var pb = (Fengsheng.pause_record_tos) message;
        player.pauseRecord(pb.getPause());
        player.send(Fengsheng.pause_record_toc.newBuilder().setPause(pb.getPause()).build());
    }
}
