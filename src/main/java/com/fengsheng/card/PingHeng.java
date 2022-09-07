package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class PingHeng extends AbstractCard {
    private static final Logger log = Logger.getLogger(PingHeng.class);

    public PingHeng(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Ping_Heng;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("平衡的使用时机不对");
            return false;
        }
        Player target = (Player) args[0];
        if (r == target) {
            log.error("平衡不能对自己使用");
            return false;
        }
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
            for (Player player : g.getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    p.send(Fengsheng.use_ping_heng_toc.newBuilder()
                            .setPlayerId(p.getAlternativeLocation(r.location())).setTargetPlayerId(p.getAlternativeLocation(target.location()))
                            .setPingHengCard(this.toPbCard()).build());
                }
            }
            g.playerDiscardCard(r, r.getCards().values().toArray(new Card[0]));
            g.playerDiscardCard(target, target.getCards().values().toArray(new Card[0]));
            r.draw(3);
            target.draw(3);
            g.getDeck().discard(this);
            return new ResolveResult(new MainPhaseIdle(r), true);
        };
        g.resolve(new OnUseCard(r, r, this, r, resolveFunc));
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "平衡";
    }
}
