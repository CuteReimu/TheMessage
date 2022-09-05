package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.network.ProtoServerChannelHandler;
import com.google.protobuf.GeneratedMessageV3;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.apache.log4j.Logger;

public class HumanPlayer extends AbstractPlayer {
    private static final Logger log = Logger.getLogger(HumanPlayer.class);

    private final Channel channel;

    public HumanPlayer(Channel channel) {
        this.channel = channel;
    }

    /**
     * 向玩家客户端发送协议
     */
    public void send(GeneratedMessageV3 message) {
        byte[] buf = message.toByteArray();
        String name = message.getDescriptorForType().getName();
        short id = ProtoServerChannelHandler.stringHash(name);
        ByteBuf byteBuf = Unpooled.buffer(4, 4);
        byteBuf.writeShortLE(buf.length + 2);
        byteBuf.writeShortLE(id);
        byteBuf.writeBytes(buf);
        channel.write(byteBuf);
        channel.writeAndFlush(buf);
        log.debug("send@%s len: %d %s | %s".formatted(channel.id().asShortText(), buf.length, name, message));
    }

    @Override
    public void notifyAddHandCard(int location, int unknownCount, Card... cards) {

    }

    @Override
    public void notifyDrawPhase() {

    }

    @Override
    public void notifyMainPhase(int waitSecond) {

    }

    @Override
    public void notifySendPhaseStart() {

    }

    @Override
    public void notifySendPhase(int waitSecond) {

    }

    @Override
    public void notifyChooseReceiveCard() {

    }

    @Override
    public void notifyFightPhase(int waitSecond) {

    }

    @Override
    public void notifyReceivePhase() {

    }

    @Override
    public void notifyReceivePhase(int waitSecond) {

    }

    @Override
    public void notifyDie(int location, boolean loseGame) {

    }

    @Override
    public void notifyWin(Player[] declareWinners, Player[] winners) {

    }

    @Override
    public void notifyAskForChengQing(Player whoDie, Player askWhom) {

    }

    @Override
    public void waitForDieGiveCard(Player whoDie) {

    }
}
