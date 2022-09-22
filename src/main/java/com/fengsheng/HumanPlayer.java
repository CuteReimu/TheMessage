package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.phase.*;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.TextFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.Timeout;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HumanPlayer extends AbstractPlayer {
    private static final Logger log = Logger.getLogger(HumanPlayer.class);
    private static final TextFormat.Printer printer = TextFormat.printer().escapingNonAscii(false);

    private Channel channel;

    private int seq;

    private Timeout timeout;

    private int timeoutCount = 0;

    private Timeout heartTimeout;

    private long lastHeartTime;

    private final Recorder recorder = new Recorder();

    private String device;

    private boolean autoPlay;

    public HumanPlayer(Channel channel) {
        this.channel = channel;
    }

    /**
     * 向玩家客户端发送协议
     */
    public void send(GeneratedMessageV3 message) {
        byte[] buf = message.toByteArray();
        final String name = message.getDescriptorForType().getName();
        short id = ProtoServerChannelHandler.stringHash(name);
        recorder.add(id, buf);
        if (isActive()) send(id, buf, true);
        log.debug("send@%s len: %d %s | %s".formatted(channel.id().asShortText(), buf.length, name,
                printer.printToString(message).replaceAll("\n *", " ")));
    }

    public void send(short id, byte[] buf, boolean flush) {
        ByteBuf byteBuf = PooledByteBufAllocator.DEFAULT.ioBuffer(buf.length + 4, buf.length + 4);
        byteBuf.writeShortLE(buf.length + 2);
        byteBuf.writeShortLE(id);
        byteBuf.writeBytes(buf);
        var f = flush ? channel.writeAndFlush(byteBuf) : channel.write(byteBuf);
        f.addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess())
                log.error("send@%s failed, id: %d, len: %d".formatted(channel.id().asShortText(), id, buf.length));
        });
    }

    public void saveRecord() {
        recorder.save(game, this, channel.isActive());
    }

    public void loadRecord(int version, String recordId) {
        recorder.load(version, recordId, this);
    }

    public boolean isLoadingRecord() {
        return recorder.loading();
    }

    public void reconnect() {
        recorder.reconnect(this);
    }

    /**
     * Return {@code true} if the {@link Channel} of the {@code HumanPlayer} is active and so connected.
     */
    public boolean isActive() {
        return channel.isActive();
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public void setAutoPlay(boolean autoPlay) {
        if (this.autoPlay == autoPlay) return;
        this.autoPlay = autoPlay;
        if (autoPlay) {
            if (timeout != null) {
                var t = timeout;
                timeout = null;
                try {
                    t.task().run(t);
                } catch (Exception e) {
                    log.error("time task exception", e);
                }
            }
        } else {
            if (timeout != null) {
                timeout.cancel();
                int delay = game.getFsm() instanceof MainPhaseIdle ? 31 : 21;
                timeout = GameExecutor.TimeWheel.newTimeout(timeout.task(), delay, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void init() {
        super.init();
        var builder = Fengsheng.init_toc.newBuilder().setPlayerCount(game.getPlayers().length).setIdentity(identity).setSecretTask(secretTask);
        int l = location;
        do {
            builder.addRoles(game.getPlayers()[l].isRoleFaceUp() || l == location ? game.getPlayers()[l].getRole() : Common.role.unknown);
            builder.addNames(game.getPlayers()[l].getPlayerName());
            l = (l + 1) % game.getPlayers().length;
        } while (l != location);
        send(builder.build());
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
            timeout = GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new SendPhaseStart(player));
                }
            }, getWaitSeconds(waitSecond + 2), TimeUnit.SECONDS);
        }
        send(builder.build());
    }

    @Override
    public void notifySendPhaseStart(int waitSecond) {
        Player player = ((SendPhaseStart) game.getFsm()).player();
        int playerId = getAlternativeLocation(player.location());
        var builder = Fengsheng.notify_phase_toc.newBuilder();
        builder.setCurrentPlayerId(playerId).setCurrentPhase(Common.phase.Send_Start_Phase);
        builder.setWaitingPlayerId(playerId).setWaitingSecond(waitSecond);
        if (this == player) {
            builder.setSeq(seq);
            final int seq2 = seq;
            timeout = GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    RobotPlayer.autoSendMessageCard(this, false);
                }
            }, getWaitSeconds(waitSecond + 2), TimeUnit.SECONDS);
        }
        send(builder.build());
    }

    public void notifySendMessageCard(Player player, Player targetPlayer, Player[] lockedPlayers, Card messageCard, Common.direction dir) {
        var builder = Fengsheng.send_message_card_toc.newBuilder();
        builder.setPlayerId(getAlternativeLocation(player.location()));
        builder.setTargetPlayerId(getAlternativeLocation(targetPlayer.location()));
        builder.setCardDir(dir);
        if (player == this) builder.setCardId(messageCard.getId());
        for (Player p : lockedPlayers)
            builder.addLockPlayerIds(getAlternativeLocation(p.location()));
        send(builder.build());
    }

    @Override
    public void notifySendPhase(int waitSecond) {
        final var fsm = (SendPhaseIdle) game.getFsm();
        int playerId = getAlternativeLocation(fsm.whoseTurn.location());
        var builder = Fengsheng.notify_phase_toc.newBuilder();
        builder.setCurrentPlayerId(playerId).setCurrentPhase(Common.phase.Send_Phase);
        builder.setMessagePlayerId(getAlternativeLocation(fsm.inFrontOfWhom.location()));
        builder.setWaitingPlayerId(getAlternativeLocation(fsm.inFrontOfWhom.location()));
        builder.setMessageCardDir(fsm.dir).setWaitingSecond(waitSecond);
        if (fsm.isMessageCardFaceUp) builder.setMessageCard(fsm.messageCard.toPbCard());
        if (this == fsm.inFrontOfWhom) {
            builder.setSeq(seq);
            final int seq2 = seq;
            timeout = GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    boolean isLocked = false;
                    for (Player p : fsm.lockedPlayers) {
                        if (p == this) {
                            isLocked = true;
                            break;
                        }
                    }
                    if (isLocked || fsm.inFrontOfWhom == fsm.whoseTurn)
                        game.resolve(new OnChooseReceiveCard(fsm.whoseTurn, fsm.messageCard, fsm.inFrontOfWhom, fsm.isMessageCardFaceUp));
                    else
                        game.resolve(new MessageMoveNext(fsm));
                }
            }, getWaitSeconds(waitSecond + 2), TimeUnit.SECONDS);
        }
        send(builder.build());
    }

    @Override
    public void notifyChooseReceiveCard(Player player) {
        send(Fengsheng.choose_receive_toc.newBuilder().setPlayerId(getAlternativeLocation(player.location())).build());
    }

    @Override
    public void notifyFightPhase(int waitSecond) {
        var fsm = (FightPhaseIdle) game.getFsm();
        var builder = Fengsheng.notify_phase_toc.newBuilder();
        builder.setCurrentPlayerId(getAlternativeLocation(fsm.whoseTurn.location()));
        builder.setMessagePlayerId(getAlternativeLocation(fsm.inFrontOfWhom.location()));
        builder.setWaitingPlayerId(getAlternativeLocation(fsm.whoseFightTurn.location()));
        builder.setCurrentPhase(Common.phase.Fight_Phase).setWaitingSecond(waitSecond);
        if (fsm.isMessageCardFaceUp) builder.setMessageCard(fsm.messageCard.toPbCard());
        if (this == fsm.whoseFightTurn) {
            builder.setSeq(seq);
            final int seq2 = seq;
            timeout = GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new FightPhaseNext(fsm));
                }
            }, getWaitSeconds(waitSecond + 2), TimeUnit.SECONDS);
        }
        send(builder.build());
    }

    @Override
    public void notifyReceivePhase() {
        var fsm = (ReceivePhase) game.getFsm();
        var builder = Fengsheng.notify_phase_toc.newBuilder();
        builder.setCurrentPlayerId(getAlternativeLocation(fsm.whoseTurn().location()));
        builder.setMessagePlayerId(getAlternativeLocation(fsm.inFrontOfWhom().location()));
        builder.setWaitingPlayerId(getAlternativeLocation(fsm.inFrontOfWhom().location()));
        builder.setCurrentPhase(Common.phase.Receive_Phase).setMessageCard(fsm.messageCard().toPbCard());
        send(builder.build());
    }

    @Override
    public void notifyReceivePhase(Player whoseTurn, Player inFrontOfWhom, Card messageCard, Player waitingPlayer, int waitSecond) {
        var builder = Fengsheng.notify_phase_toc.newBuilder();
        builder.setCurrentPlayerId(getAlternativeLocation(whoseTurn.location()));
        builder.setMessagePlayerId(getAlternativeLocation(inFrontOfWhom.location()));
        builder.setWaitingPlayerId(getAlternativeLocation(waitingPlayer.location()));
        builder.setCurrentPhase(Common.phase.Receive_Phase).setMessageCard(messageCard.toPbCard()).setWaitingSecond(waitSecond);
        if (this == waitingPlayer) {
            builder.setSeq(seq);
            final int seq2 = seq;
            timeout = GameExecutor.post(game, () -> {
                if (checkSeq(seq2))
                    game.tryContinueResolveProtocol(this, Fengsheng.end_receive_phase_tos.newBuilder().setSeq(seq2).build());
            }, getWaitSeconds(waitSecond + 2), TimeUnit.SECONDS);
        }
        send(builder.build());
    }

    @Override
    public void notifyDying(int location, boolean loseGame) {
        super.notifyDying(location, loseGame);
        send(Fengsheng.notify_dying_toc.newBuilder().setPlayerId(getAlternativeLocation(location)).setLoseGame(loseGame).build());
    }

    @Override
    public void notifyDie(int location) {
        super.notifyDie(location);
        send(Fengsheng.notify_die_toc.newBuilder().setPlayerId(getAlternativeLocation(location)).build());
    }

    @Override
    public void notifyWin(Player[] declareWinners, Player[] winners) {
        var builder = Fengsheng.notify_winner_toc.newBuilder();
        List<Integer> declareWinnerIds = new ArrayList<>();
        for (Player p : declareWinners)
            declareWinnerIds.add(getAlternativeLocation(p.location()));
        Collections.sort(declareWinnerIds);
        builder.addAllDeclarePlayerIds(declareWinnerIds);
        List<Integer> winnerIds = new ArrayList<>();
        for (Player p : winners)
            winnerIds.add(getAlternativeLocation(p.location()));
        Collections.sort(winnerIds);
        builder.addAllWinnerIds(winnerIds);
        for (int i = 0; i < game.getPlayers().length; i++) {
            Player p = game.getPlayers()[(location + i) % game.getPlayers().length];
            builder.addIdentity(p.getIdentity()).addSecretTasks(p.getSecretTask());
        }
        send(builder.build());
    }

    @Override
    public void notifyAskForChengQing(Player whoDie, Player askWhom, int waitSecond) {
        var builder = Fengsheng.wait_for_cheng_qing_toc.newBuilder();
        builder.setDiePlayerId(getAlternativeLocation(whoDie.location()));
        builder.setWaitingPlayerId(getAlternativeLocation(askWhom.location()));
        builder.setWaitingSecond(waitSecond);
        if (askWhom == this) {
            builder.setSeq(seq);
            final int seq2 = seq;
            timeout = GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new WaitNextForChengQing((WaitForChengQing) game.getFsm()));
                }
            }, getWaitSeconds(waitSecond + 2), TimeUnit.SECONDS);
        }
        send(builder.build());
    }

    @Override
    public void waitForDieGiveCard(Player whoDie, int waitSecond) {
        var builder = Fengsheng.wait_for_die_give_card_toc.newBuilder();
        builder.setPlayerId(getAlternativeLocation(whoDie.location()));
        builder.setWaitingSecond(waitSecond);
        if (whoDie == this) {
            builder.setSeq(seq);
            final int seq2 = seq;
            timeout = GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new AfterDieGiveCard((WaitForDieGiveCard) game.getFsm()));
                }
            }, getWaitSeconds(waitSecond + 2), TimeUnit.SECONDS);
        }
        send(builder.build());
    }

    /**
     * 把跟玩家有关的计时器绑定在玩家身上，例如操作超时等待。这样在玩家操作后就可以清掉这个计时器，以节约资源
     */
    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
    }

    /**
     * 心跳相关计时器
     */
    public void setHeartTimeout() {
        if (heartTimeout != null) heartTimeout.cancel();
        heartTimeout = GameExecutor.TimeWheel.newTimeout(timeout -> {
            log.info(this + " heart timeout, duration " + (System.currentTimeMillis() - lastHeartTime) + " ms");
            if (channel.isActive()) channel.close();
        }, 30, TimeUnit.SECONDS);
        lastHeartTime = System.currentTimeMillis();
    }

    public void clearHeartTimeout() {
        if (heartTimeout != null) {
            heartTimeout.cancel();
            heartTimeout = null;
        }
    }

    public int getSeq() {
        return seq;
    }

    public boolean checkSeq(int seq) {
        return this.seq == seq;
    }

    @Override
    public void incrSeq() {
        seq++;
        if (timeout != null) {
            if (timeout.isExpired()) {
                if (++timeoutCount >= 3)
                    setAutoPlay(true);
            } else {
                timeout.cancel();
            }
            timeout = null;
        }
    }

    public void clearTimeoutCount() {
        timeoutCount = 0;
    }

    public int getWaitSeconds(int seconds) {
        if (!isActive()) {
            return 5;
        }
        return autoPlay ? 1 : seconds;
    }
}
