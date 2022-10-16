package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class WuDao extends Card {
    private static final Logger log = Logger.getLogger(WuDao.class);

    public WuDao(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public WuDao(int id, Card card) {
        super(id, card);
    }

    /**
     * 仅用于“作为误导使用”
     */
    WuDao(Card originCard) {
        super(originCard);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Wu_Dao;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        if (r == g.getJinBiPlayer()) {
            log.error("你被禁闭了，不能出牌");
            return false;
        }
        if (g.getQiangLingTypes().contains(getType())) {
            log.error("误导被禁止使用了");
            return false;
        }
        Player target = (Player) args[0];
        if (!(g.getFsm() instanceof FightPhaseIdle fsm)) {
            log.error("误导的使用时机不对");
            return false;
        }
        Player left = fsm.inFrontOfWhom.getNextLeftAlivePlayer();
        Player right = fsm.inFrontOfWhom.getNextRightAlivePlayer();
        if (target == fsm.inFrontOfWhom || (target != left && target != right)) {
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
            g.getDeck().discard(this.getOriginCard());
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
        g.resolve(new OnUseCard(fsm.whoseTurn, r, null, this, Common.card_type.Wu_Dao, r, resolveFunc));
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
        Player target = switch (ThreadLocalRandom.current().nextInt(4)) {
            case 0 -> e.inFrontOfWhom.getNextLeftAlivePlayer();
            case 1 -> e.inFrontOfWhom.getNextRightAlivePlayer();
            default -> null;
        };
        if (target == null) return false;
        final Player finalTarget = target;
        GameExecutor.post(player.getGame(), () -> {
            Card card0 = card.getType() == Common.card_type.Wu_Dao ? card : Card.falseCard(Common.card_type.Wu_Dao, card);
            card0.execute(player.getGame(), player, finalTarget);
        }, 2, TimeUnit.SECONDS);
        return true;
    }
}
