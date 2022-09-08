package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.phase.SendPhaseIdle;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class PoYi extends AbstractCard {
    private static final Logger log = Logger.getLogger(PoYi.class);

    public PoYi(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public PoYi(int id, AbstractCard card) {
        super(id, card);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Po_Yi;
    }

    @Override
    public boolean canUse(final Game g, final Player r, Object... args) {
        if (!(g.getFsm() instanceof SendPhaseIdle fsm) || r != fsm.inFrontOfWhom) {
            log.error("破译的使用时机不对");
            return false;
        }
        if (fsm.isMessageCardFaceUp) {
            log.error("破译不能对已翻开的情报使用");
            return false;
        }
        return true;
    }

    @Override
    public void execute(Game g, Player r, Object... args) {
        var fsm = (SendPhaseIdle) g.getFsm();
        log.info(r + "使用了" + this);
        r.deleteCard(this.id);
        Fsm resolveFunc = () -> new ResolveResult(new executePoYi(this, fsm), true);
        g.resolve(new OnUseCard(r, r, this, r, resolveFunc));
    }

    private record executePoYi(PoYi card, SendPhaseIdle sendPhase) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executePoYi.class);

        @Override
        public ResolveResult resolve() {
            Player r = sendPhase.inFrontOfWhom;
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Fengsheng.use_po_yi_toc.newBuilder();
                    builder.setCard(card.toPbCard()).setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setMessageCard(sendPhase.messageCard.toPbCard()).setWaitingSecond(20);
                    if (p == r) {
                        final int seq2 = p.getSeq();
                        builder.setSeq(seq2).setCard(card.toPbCard());
                        p.setTimeout(GameExecutor.post(r.getGame(), () -> {
                            if (p.checkSeq(seq2)) {
                                p.incrSeq();
                                showAndDrawCard(false);
                                r.getGame().resolve(sendPhase);
                            }
                        }, builder.getWaitingSecond() + 2, TimeUnit.SECONDS));
                    }
                    p.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(r.getGame(), () -> {
                    showAndDrawCard(sendPhase.messageCard.getColors().contains(Common.color.Black));
                    r.getGame().resolve(sendPhase);
                }, 2, TimeUnit.SECONDS);
            }
            return new ResolveResult(this, false);
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (!(message instanceof Fengsheng.po_yi_show_tos msg)) {
                log.error("现在正在结算破译");
                return new ResolveResult(this, false);
            }
            if (player != sendPhase.inFrontOfWhom) {
                log.error("你不是破译的使用者");
                return new ResolveResult(this, false);
            }
            if (msg.getShow() && !sendPhase.messageCard.getColors().contains(Common.color.Black)) {
                log.error("非黑牌不能翻开");
                return new ResolveResult(this, false);
            }
            player.incrSeq();
            showAndDrawCard(msg.getShow());
            return new ResolveResult(sendPhase, true);
        }

        private void showAndDrawCard(boolean show) {
            Player r = sendPhase.inFrontOfWhom;
            if (show) {
                log.info(sendPhase.messageCard + "被翻开了");
                sendPhase.isMessageCardFaceUp = true;
                r.draw(1);
            }
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Fengsheng.po_yi_show_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location())).setShow(show);
                    if (show) builder.setMessageCard(sendPhase.messageCard.toPbCard());
                    p.send(builder.build());
                }
            }
            r.getGame().getDeck().discard(card);
        }
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "破译";
    }

    public static boolean ai(SendPhaseIdle e, Card card) {
        Player player = e.inFrontOfWhom;
        if (player == e.whoseTurn || e.isMessageCardFaceUp) return false;
        if (ThreadLocalRandom.current().nextBoolean()) return false;
        GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player), 2, TimeUnit.SECONDS);
        return true;
    }
}
