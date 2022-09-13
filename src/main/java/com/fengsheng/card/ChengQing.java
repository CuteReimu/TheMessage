package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.phase.WaitForChengQing;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ChengQing extends AbstractCard {
    private static final Logger log = Logger.getLogger(ChengQing.class);

    public ChengQing(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public ChengQing(int id, AbstractCard card) {
        super(id, card);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Cheng_Qing;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        Player target = (Player) args[0];
        int targetCardId = (Integer) args[1];
        Fsm fsm = g.getFsm();
        if (fsm instanceof MainPhaseIdle) {
            if (r != ((MainPhaseIdle) fsm).player()) {
                log.error("澄清的使用时机不对");
                return false;
            }
        } else if (fsm instanceof WaitForChengQing) {
            if (r != ((WaitForChengQing) fsm).askWhom) {
                log.error("澄清的使用时机不对");
                return false;
            }
        } else {
            log.error("澄清的使用时机不对");
            return false;
        }
        if (!target.isAlive()) {
            log.error("目标已死亡");
            return false;
        }
        Card targetCard = target.findMessageCard(targetCardId);
        if (targetCard == null) {
            log.error("没有这张情报");
            return false;
        }
        if (!targetCard.getColors().contains(Common.color.Black)) {
            log.error("澄清只能对黑情报使用");
            return false;
        }
        return true;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {
        final Player target = (Player) args[0];
        final int targetCardId = (Integer) args[1];
        log.info(r + "对" + target + "使用了" + this);
        r.deleteCard(this.id);
        final Fsm fsm = g.getFsm();
        Fsm resolveFunc = () -> {
            Card targetCard = target.deleteMessageCard(targetCardId);
            log.info(target + "面前的" + targetCard + "被置入弃牌堆");
            g.getDeck().discard(targetCard);
            for (Player player : g.getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    p.send(Fengsheng.use_cheng_qing_toc.newBuilder()
                            .setCard(this.toPbCard()).setPlayerId(p.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(p.getAlternativeLocation(target.location())).setTargetCardId(targetCardId).build());
                }
            }
            g.getDeck().discard(this);
            return new ResolveResult(fsm, true);
        };
        if (fsm instanceof MainPhaseIdle)
            g.resolve(new OnUseCard(((MainPhaseIdle) fsm).player(), r, this, r, resolveFunc));
        else if (fsm instanceof WaitForChengQing)
            g.resolve(new OnUseCard(((WaitForChengQing) fsm).whoseTurn, r, this, r, resolveFunc));
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "澄清";
    }

    public static boolean ai(MainPhaseIdle e, Card card) {
        Player player = e.player();
        List<PlayerAndCard> playerAndCards = new ArrayList<>();
        var identity = player.getIdentity();
        for (Player p : player.getGame().getPlayers()) {
            if ((p.equals(player) || identity != Common.color.Black && identity == p.getIdentity()) && p.isAlive()) {
                for (Card c : p.getMessageCards().values()) {
                    if (c.getColors().contains(Common.color.Black))
                        playerAndCards.add(new PlayerAndCard(p, c));
                }
            }
        }
        if (playerAndCards.isEmpty()) return false;
        PlayerAndCard p = playerAndCards.get(ThreadLocalRandom.current().nextInt(playerAndCards.size()));
        GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player, p.player(), p.card().getId()), 2, TimeUnit.SECONDS);
        return true;
    }

    private record PlayerAndCard(Player player, Card card) {

    }
}
