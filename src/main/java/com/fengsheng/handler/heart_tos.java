package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;

public class heart_tos implements ProtoHandler {
    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        player.setHeartTimeout();
        player.send(Fengsheng.heart_toc.getDefaultInstance());
    }
}
