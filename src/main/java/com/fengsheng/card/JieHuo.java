package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import org.apache.log4j.Logger;

public class JieHuo extends AbstractCard {
    private static final Logger log = Logger.getLogger(JieHuo.class);

    public JieHuo(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Jie_Huo;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        Fsm fsm = g.getFsm();
        if (!(fsm instanceof FightPhaseIdle) || r != ((FightPhaseIdle) fsm).whoseFightTurn) {
            log.error("截获的使用时机不对");
            return false;
        }
        if (r == ((FightPhaseIdle) fsm).inFrontOfWhom) {
            log.error("情报在自己面前不能使用截获");
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
            fsm.inFrontOfWhom = r;
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            g.getDeck().discard(this);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer) {
                    ((HumanPlayer) p).send(Fengsheng.use_jie_huo_toc.newBuilder().setCard(this.toPbCard())
                            .setPlayerId(p.getAlternativeLocation(r.location())).build());
                }
            }
            return new ResolveResult(fsm, true);
        };
        g.resolve(new OnUseCard(fsm.whoseTurn, r, this, r, resolveFunc));
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "截获";
    }
}
