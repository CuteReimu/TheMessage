package com.fengsheng.card;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.protos.Common;

public class PingHeng extends AbstractCard {
    public PingHeng(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    @Override
    public Common.card_type getType() {
        return null;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        return false;
    }

    @Override
    public void execute(Game g, Player r, Object... args) {

    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "平衡";
    }
}
