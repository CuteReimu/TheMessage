package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class WuDao extends AbstractCard {
    private static final Logger log = Logger.getLogger(WuDao.class);

    public WuDao(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public WuDao(int id, AbstractCard card) {
        super(id, card);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Wu_Dao;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        Player target = (Player) args[0];
        if (!(g.getFsm() instanceof FightPhaseIdle fsm)) {
            log.error("误导的使用时机不对");
            return false;
        }
        int left, right;
        for (left = fsm.inFrontOfWhom.location() - 1; left != fsm.inFrontOfWhom.location(); left--) {
            if (left < 0) left += g.getPlayers().length;
            if (g.getPlayers()[left].isAlive()) break;
        }
        for (right = fsm.inFrontOfWhom.location() + 1; right != fsm.inFrontOfWhom.location(); right++) {
            if (right >= g.getPlayers().length) right -= g.getPlayers().length;
            if (g.getPlayers()[right].isAlive()) break;
        }
        if (target == fsm.inFrontOfWhom || (target.location() != left && target.location() != right)) {
            log.error("误导只能选择情报当前人左右两边的人作为目标");
            return false;
        }
        return true;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {
        Player target = (Player) args[0];
        log.info(r + "对" + target + "使用了" + this);
        final var fsm = (FightPhaseIdle) g.getFsm();
        r.deleteCard(this.id);
        Fsm resolveFunc = () -> {
            fsm.inFrontOfWhom = target;
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            g.getDeck().discard(this);
            for (Player player : g.getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Fengsheng.use_wu_dao_toc.newBuilder().setCard(this.toPbCard());
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                    p.send(builder.build());
                }
            }
            return new ResolveResult(fsm, true);
        };
        g.resolve(new OnUseCard(fsm.whoseTurn, r, this, r, resolveFunc));
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "误导";
    }

    public static boolean ai(FightPhaseIdle e, Card card) {
        Player player = e.whoseFightTurn;
        var colors = e.messageCard.getColors();
        if (e.inFrontOfWhom == player && (e.isMessageCardFaceUp || player == e.whoseTurn) && colors.size() == 1 && colors.get(0) != Common.color.Black)
            return false;
        Player[] players = player.getGame().getPlayers();
        Player target = null;
        switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> {
                for (int left = e.inFrontOfWhom.location() - 1; left != e.inFrontOfWhom.location(); left--) {
                    if (left < 0) left += players.length;
                    if (player.getGame().getPlayers()[left].isAlive()) {
                        target = players[left];
                        break;
                    }
                }
            }
            case 1 -> {
                for (int right = e.inFrontOfWhom.location() + 1; right != e.inFrontOfWhom.location(); right++) {
                    if (right >= players.length) right -= players.length;
                    if (player.getGame().getPlayers()[right].isAlive()) {
                        target = players[right];
                        break;
                    }
                }
            }
            default -> {
                return false;
            }
        }
        if (target == null) return false;
        final Player finalTarget = target;
        GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player, finalTarget), 2, TimeUnit.SECONDS);
        return true;
    }
}
