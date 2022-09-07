package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

import java.util.Arrays;

public class LiYou extends AbstractCard {
    private static final Logger log = Logger.getLogger(LiYou.class);

    public LiYou(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Li_You;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        Fsm fsm = g.getFsm();
        if (!(fsm instanceof MainPhaseIdle) || r != ((MainPhaseIdle) fsm).player()) {
            log.error("利诱的使用时机不对");
            return false;
        }
        Player target = (Player) args[0];
        if (!target.isAlive()) {
            log.error("目标已死亡");
            return false;
        }
        return true;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {
        final Player target = (Player) args[0];
        log.info(r + "对" + target + "使用了" + this);
        r.deleteCard(this.id);
        Fsm resolveFunc = () -> {
            Card[] deckCards = g.getDeck().draw(1);
            boolean joinIntoHand = false;
            if (deckCards.length > 0) {
                target.addMessageCard(deckCards);
                if (target.checkThreeSameMessageCard(deckCards[0].getColors().toArray(new Common.color[0]))) {
                    target.deleteMessageCard(deckCards[0].getId());
                    joinIntoHand = true;
                    r.addCard(deckCards);
                    log.info(Arrays.toString(deckCards) + "加入了" + r + "的手牌");
                } else {
                    log.info(Arrays.toString(deckCards) + "加入了" + target + "的情报区");
                }
            }
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer) {
                    var builder = Fengsheng.use_li_you_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                    builder.setLiYouCard(this.toPbCard()).setJoinIntoHand(joinIntoHand);
                    if (deckCards.length > 0) builder.setMessageCard(deckCards[0].toPbCard());
                    ((HumanPlayer) p).send(builder.build());
                }
            }
            g.getDeck().discard(this);
            return new ResolveResult(new MainPhaseIdle(r), true);
        };
        g.resolve(new OnUseCard(r, r, this, r, resolveFunc));
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "利诱";
    }
}
