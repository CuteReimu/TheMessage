package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class DiaoBao extends AbstractCard {
    private static final Logger log = Logger.getLogger(DiaoBao.class);

    public DiaoBao(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public DiaoBao(int id, AbstractCard card) {
        super(id, card);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Diao_Bao;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("调包的使用时机不对");
            return false;
        }
        return true;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {
        final var fsm = (FightPhaseIdle) g.getFsm();
        log.info(r + "使用了" + this);
        r.deleteCard(this.id);
        Fsm resolveFunc = () -> {
            Card oldCard = fsm.messageCard;
            g.getDeck().discard(oldCard);
            fsm.messageCard = this;
            fsm.isMessageCardFaceUp = false;
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            for (Player player : g.getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Fengsheng.use_diao_bao_toc.newBuilder();
                    builder.setOldMessageCard(oldCard.toPbCard()).setPlayerId(p.getAlternativeLocation(r.location()));
                    if (p == r) builder.setCardId(this.id);
                    p.send(builder.build());
                }
            }
            return new ResolveResult(fsm, true);
        };
        g.resolve(new OnUseCard(fsm.whoseTurn, r, this, r, resolveFunc));
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "调包";
    }

    public static class Ai implements BiFunction<FightPhaseIdle, Card, Boolean> {
        @Override
        public Boolean apply(FightPhaseIdle e, Card card) {
            Player player = e.whoseFightTurn;
            var colors = e.messageCard.getColors();
            if (e.inFrontOfWhom == player && (e.isMessageCardFaceUp || player == e.whoseTurn) && colors.size() == 1 && colors.get(0) != Common.color.Black)
                return false;
            if (ThreadLocalRandom.current().nextInt(4) != 0)
                return false;
            GameExecutor.TimeWheel.newTimeout(timeout -> GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player)), 2, TimeUnit.SECONDS);
            return true;
        }
    }
}
