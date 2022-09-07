package com.fengsheng.card;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.protos.Common;
import org.apache.log4j.Logger;

public class WuDao extends AbstractCard {
    private static final Logger log = Logger.getLogger(WuDao.class);

    public WuDao(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Wu_Dao;
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
        return Card.cardColorToString(colors) + "误导";
    }
}
