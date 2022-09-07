package com.fengsheng.card;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.protos.Common;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ShiTan extends AbstractCard {
    private static final Logger log = Logger.getLogger(ShiTan.class);

    private final Common.color[] whoDrawCard;

    public ShiTan(int id, Common.color[] colors, Common.direction direction, boolean lockable, Common.color[] whoDrawCard) {
        super(id, colors, direction, lockable);
        this.whoDrawCard = whoDrawCard;
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Shi_Tan;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        return false;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {

    }

    public Common.color[] getWhoDrawCard() {
        return whoDrawCard;
    }

    @Override
    public Common.card toPbCard() {
        return Common.card.newBuilder().setCardId(id).setCardDir(direction).setCanLock(lockable).setCardType(getType()).addAllCardColor(colors).addAllWhoDrawCard(Arrays.asList(whoDrawCard)).build();
    }

    @Override
    public String toString() {
        String color = Card.cardColorToString(colors);
        if (whoDrawCard.length == 1)
            return Player.identityColorToString(whoDrawCard[0]) + "+1试探";
        Set<Common.color> set = new HashSet<>(List.of(Common.color.Black, Common.color.Red, Common.color.Blue));
        set.remove(whoDrawCard[0]);
        set.remove(whoDrawCard[1]);
        for (Common.color whoDiscardCard : set) {
            return color + Player.identityColorToString(whoDiscardCard) + "-1试探";
        }
        throw new RuntimeException("impossible whoDrawCard: " + Arrays.toString(whoDrawCard));
    }
}
