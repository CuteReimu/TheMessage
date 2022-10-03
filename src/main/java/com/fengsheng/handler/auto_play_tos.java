package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Fengsheng;

public class auto_play_tos extends AbstractProtoHandler<Fengsheng.auto_play_tos> {
    @Override
    public void handle0(HumanPlayer player, Fengsheng.auto_play_tos pb) {
        player.setAutoPlay(pb.getEnable());
    }
}
