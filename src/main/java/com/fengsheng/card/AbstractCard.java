package com.fengsheng.card;

import com.fengsheng.protos.Common;

import java.util.List;

public abstract class AbstractCard implements Card {
    private final int id;
    private final List<Common.color> colors;
    private final Common.direction direction;
    private final boolean lockable;

    protected AbstractCard(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
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
