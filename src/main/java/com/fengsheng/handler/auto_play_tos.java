package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.protos.Fengsheng;

public class auto_play_tos extends AbstractProtoHandler<Fengsheng.auto_play_tos> {
    private static final short msgId = ProtoServerChannelHandler.stringHash("auto_play_toc");

    @Override
    public void handle0(HumanPlayer player, Fengsheng.auto_play_tos pb) {
        player.setAutoPlay(pb.getEnable());
        player.send(msgId, Fengsheng.auto_play_toc.newBuilder().setEnable(pb.getEnable()).build().toByteArray(), true);
    }
}
