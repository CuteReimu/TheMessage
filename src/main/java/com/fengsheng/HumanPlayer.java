package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.network.ProtoServerChannelHandler;
import com.fengsheng.phase.*;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.RoleSkillsData;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.TextFormat;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.util.Timeout;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class HumanPlayer extends AbstractPlayer {
    private static final Logger log = Logger.getLogger(HumanPlayer.class);
    private static final TextFormat.Printer printer = TextFormat.printer().escapingNonAscii(false);

    private final Channel channel;

    private int seq;

    private Timeout timeout;

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
                printer.printToString(message).replaceAll("\n *", " ")));
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
            timeout = GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new SendPhaseStart(player));
                }
            }), waitSecond + 2, TimeUnit.SECONDS);
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
            timeout = GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    RobotPlayer.autoSendMessageCard(this, false);
                }
            }), waitSecond + 2, TimeUnit.SECONDS);
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
            timeout = GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(game, () -> {
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
            }), waitSecond + 2, TimeUnit.SECONDS);
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
            timeout = GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new FightPhaseNext(fsm));
                }
            }), waitSecond + 2, TimeUnit.SECONDS);
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
            timeout = GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(game, () -> {
                if (checkSeq(seq2))
                    game.tryContinueResolveProtocol(this, Fengsheng.end_receive_phase_tos.newBuilder().setSeq(seq2).build());
            }), waitSecond + 2, TimeUnit.SECONDS);
        }
        send(builder.build());
    }

    @Override
    public void notifyDie(int location, boolean loseGame) {
        super.notifyDie(location, loseGame);
        send(Fengsheng.notify_die_toc.newBuilder().setPlayerId(getAlternativeLocation(location)).setLoseGame(loseGame).build());
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
        builder.addAllDeclarePlayerIds(winnerIds);
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
        builder.setWaitingSecond(getAlternativeLocation(askWhom.location()));
        builder.setWaitingSecond(waitSecond);
        if (askWhom == this) {
            builder.setSeq(seq);
            final int seq2 = seq;
            timeout = GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new WaitNextForChengQing((WaitForChengQing) game.getFsm()));
                }
            }), waitSecond + 2, TimeUnit.SECONDS);
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
            timeout = GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(game, () -> {
                if (checkSeq(seq2)) {
                    incrSeq();
                    game.resolve(new AfterDieGiveCard((WaitForDieGiveCard) game.getFsm()));
                }
            }), waitSecond + 2, TimeUnit.SECONDS);
        }
    }

    public void setTimeout(Timeout timeout) {
        this.timeout = timeout;
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
            timeout.cancel();
            timeout = null;
        }
    }

    @Override
    public String toString() {
        return location + "号[" + (isRoleFaceUp() ? roleSkillsData.getName() : "玩家") + "]";
    }
}
