package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

public class display_record_tos implements ProtoHandler {
    private static final Logger log = Logger.getLogger(display_record_tos.class);

    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        if (player.getGame() != null) {
            log.error("player is already in a room");
            return;
        }
        var pb = (Fengsheng.display_record_tos) message;
        player.loadRecord(pb.getVersion(), pb.getRecordId());
    }
}
