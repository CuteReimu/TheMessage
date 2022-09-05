package com.fengsheng.card;

import com.fengsheng.protos.Common;

import java.util.List;

public abstract class AbstractCard implements Card {
    protected final int id;
    protected final List<Common.color> colors;
    protected final Common.direction direction;
    protected final boolean lockable;

    public AbstractCard(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        this.id = id;
        this.colors = List.of(colors);
        this.direction = direction;
        this.lockable = lockable;
    }

    @Override
    public int getId() {
        return id;
    }

    @Override
    public List<Common.color> getColors() {
        return colors;
    }

    @Override
    public Common.direction getDirection() {
        return direction;
    }

    @Override
    public boolean canLock() {
        return lockable;
    }

    @Override
    public Common.card toPbCard() {
        return Common.card.newBuilder().setCardId(id).setCardDir(direction).setCanLock(lockable).addAllCardColor(colors).build();
    }
}
