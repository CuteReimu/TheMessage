package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;

public class auto_play_tos implements ProtoHandler {
    private static final short msgId = ProtoServerChannelHandler.stringHash("auto_play_toc");

    @Override
    public void handle(HumanPlayer player, GeneratedMessageV3 message) {
        var pb = (Fengsheng.auto_play_tos) message;
        player.setAutoPlay(pb.getEnable());
        player.send(msgId, Fengsheng.auto_play_toc.newBuilder().setEnable(pb.getEnable()).build().toByteArray(), true);
    }
}
