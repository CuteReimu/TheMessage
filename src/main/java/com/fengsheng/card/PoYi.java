package com.fengsheng.card;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.protos.Common;
import org.apache.log4j.Logger;

public class PoYi extends AbstractCard {
    private static final Logger log = Logger.getLogger(PoYi.class);

    public PoYi(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Po_Yi;
    }

    @Override
    public boolean canUse(final Game g, final Player r, Object... args) {
        return false;
    }

    @Override
    public void execute(Game g, Player r, Object... args) {

    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "破译";
    }
}
