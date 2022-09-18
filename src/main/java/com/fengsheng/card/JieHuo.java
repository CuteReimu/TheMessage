package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class JieHuo extends AbstractCard {
    private static final Logger log = Logger.getLogger(JieHuo.class);

    public JieHuo(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public JieHuo(int id, AbstractCard card) {
        super(id, card);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Jie_Huo;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        return JieHuo.canUse(g, r);
    }

    public static boolean canUse(Game g, Player r) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("截获的使用时机不对");
            return false;
        }
        if (r == fsm.inFrontOfWhom) {
            log.error("情报在自己面前不能使用截获");
            return false;
        }
        return true;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {
        log.info(r + "使用了" + this);
        r.deleteCard(this.id);
        JieHuo.execute(this, g, r);
    }

    /**
     * 执行【截获】的效果
     *
     * @param card 使用的那张【截获】卡牌。可以为 {@code null} ，因为鄭文先技能【偷天】可以视为使用了【截获】。
     */
    public static void execute(JieHuo card, final Game g, final Player r) {
        final var fsm = (FightPhaseIdle) g.getFsm();
        Fsm resolveFunc = () -> {
            fsm.inFrontOfWhom = r;
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            if (card != null) g.getDeck().discard(card);
            for (Player player : g.getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Fengsheng.use_jie_huo_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    if (card != null) builder.setCard(card.toPbCard());
                    p.send(builder.build());
                }
            }
            return new ResolveResult(fsm, true);
        };
        if (card != null)
            g.resolve(new OnUseCard(fsm.whoseTurn, r, card, r, resolveFunc));
        else
            g.resolve(resolveFunc);
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "截获";
    }

    public static boolean ai(FightPhaseIdle e, Card card) {
        Player player = e.whoseFightTurn;
        var colors = e.messageCard.getColors();
        if (e.inFrontOfWhom == player || (e.isMessageCardFaceUp || player == e.whoseTurn) && colors.size() == 1 && colors.get(0) == Common.color.Black)
            return false;
        if (ThreadLocalRandom.current().nextBoolean())
            return false;
        GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player), 2, TimeUnit.SECONDS);
        return true;
    }
}
