package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;

public class heart_tos implements ProtoHandler {
    private static final short msgId = ProtoServerChannelHandler.stringHash("heart_toc");
    private static final byte[] buf = Fengsheng.heart_toc.getDefaultInstance().toByteArray();

    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        player.setHeartTimeout();
        player.send(msgId, buf, true);
    }
}
