package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.phase.DrawPhase;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.phase.SendPhaseStart;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.RoleSkillsData;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.TextFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.Timer;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

public class HumanPlayer extends AbstractPlayer {
    private static final Logger log = Logger.getLogger(HumanPlayer.class);
    private static final TextFormat.Printer printer = TextFormat.printer().escapingNonAscii(false);

    private final Channel channel;

    private int seq;

    private Timer timer;

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
        ByteBuf byteBuf = Unpooled.buffer(buf.length + 4, buf.length + 4);
        byteBuf.writeShortLE(buf.length + 2);
        byteBuf.writeShortLE(id);
        byteBuf.writeBytes(buf);
        channel.write(byteBuf);
        channel.writeAndFlush(buf);
        log.debug("send@%s len: %d %s | %s".formatted(channel.id().asShortText(), buf.length, name,
                printer.printToString(message).replace("\n", " ")));
    }

    @Override
    public void init(Common.color identity, Common.secret_task secretTask, RoleSkillsData roleSkillsData) {
        super.init(identity, secretTask, roleSkillsData);
        var builder = Fengsheng.init_toc.newBuilder().setPlayerCount(game.getPlayers().length).setIdentity(identity).setSecretTask(secretTask);
        int l = location;
        do {
            // TODO 需要通知玩家其他人的角色
            builder.addRoles(Common.role.unknown);
            l = (l + 1) % game.getPlayers().length;
        } while (l != location);
        send(builder.build());
        seq++;
    }

    @Override
    public void notifyAddHandCard(int location, int unknownCount, Card... cards) {
        var builder = Fengsheng.add_card_toc.newBuilder().setPlayerId(getAlternativeLocation(location)).setUnknownCardCount(unknownCount);
        for (Card card : cards) {
            builder.addCards(card.toPbCard());
        }
        send(builder.build());
    }

    @Override
    public void notifyDrawPhase() {
        Player player = ((DrawPhase) game.getFsm()).player();
        int playerId = getAlternativeLocation(player.location());
        var builder = Fengsheng.notify_phase_toc.newBuilder();
        builder.setCurrentPlayerId(playerId).setCurrentPhase(Common.phase.Draw_Phase).setWaitingPlayerId(playerId);
        send(builder.build());
    }

    @Override
    public void notifyMainPhase(int waitSecond) {
        Player player = ((MainPhaseIdle) game.getFsm()).player();
        int playerId = getAlternativeLocation(player.location());
        var builder = Fengsheng.notify_phase_toc.newBuilder();
        builder.setCurrentPlayerId(playerId).setCurrentPhase(Common.phase.Main_Phase).setWaitingPlayerId(playerId);
        builder.setWaitingSecond(waitSecond);
        if (this == player) {
            builder.setSeq(seq);
            final int seq2 = seq;
            timer = GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new SendPhaseStart(player));
                }
            }), waitSecond + 2, TimeUnit.SECONDS).timer();
        }
        send(builder.build());
    }

    @Override
    public void notifySendPhaseStart(int waitSecond) {

    }

    public void notifySendMessageCard(Player player, Player targetPlayer, Player[] lockedPlayers, Card messageCard, Common.direction direction) {

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
    public void notifyReceivePhase(Player waitingPlayer, int waitSecond) {

    }

    @Override
    public void notifyDie(int location, boolean loseGame) {
        super.notifyDie(location, loseGame);
        send(Fengsheng.notify_die_toc.newBuilder().setPlayerId(getAlternativeLocation(location)).setLoseGame(loseGame).build());
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

    public boolean checkSeq(int seq) {
        return this.seq == seq;
    }

    @Override
    public void incrSeq() {
        seq++;
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }

    @Override
    public String toString() {
        return location + "号[" + (isRoleFaceUp() ? roleSkillsData.getName() : "玩家") + "]";
    }
}
