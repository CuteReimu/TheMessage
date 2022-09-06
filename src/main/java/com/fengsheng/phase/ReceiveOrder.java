package com.fengsheng.phase;

import com.fengsheng.Player;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;

import java.util.LinkedList;

/**
 * 角色接收第三张黑色情报的顺序
 */
public class ReceiveOrder extends LinkedList<Player> {
    public void addPlayerIfHasThreeBlack(Player player) {
        if (contains(player)) return;
        int count = 0;
        for (Card card : player.getMessageCards().values()) {
            for (Common.color color : card.getColors()) {
                if (color == Common.color.Black) count++;
            }
        }
        if (count >= 3) add(player);
    }

    public void removePlayerIfNotHaveThreeBlack(Player player) {
        int count = 0;
        for (Card card : player.getMessageCards().values()) {
            for (Common.color color : card.getColors()) {
                if (color == Common.color.Black) count++;
            }
        }
        if (count < 3) remove(player);
    }
}
