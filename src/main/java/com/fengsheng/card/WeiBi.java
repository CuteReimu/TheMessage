package com.fengsheng.card;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.protos.Common;
import org.apache.log4j.Logger;

public class WeiBi extends AbstractCard {
    private static final Logger log = Logger.getLogger(WeiBi.class);

    public WeiBi(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Wei_Bi;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        return false;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {

    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "威逼";
    }
}
