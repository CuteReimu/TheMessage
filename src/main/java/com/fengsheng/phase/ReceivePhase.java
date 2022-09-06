package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 接收阶段（确定接收后，即将发动接收时的技能）
 *
 * @param whoseTurn     谁的回合
 * @param messageCard   情报牌
 * @param inFrontOfWhom 情报在谁面前
 */
public record ReceivePhase(Player whoseTurn, Card messageCard, Player inFrontOfWhom) implements Fsm {
    private static final Logger log = Logger.getLogger(ReceivePhase.class);

    @Override
    public ResolveResult resolve() {
        Player player = inFrontOfWhom;
        if (player.isAlive()) {
            player.addMessageCard(messageCard);
            log.info(player + "成功接收情报");
            for (Player p : player.getGame().getPlayers())
                p.notifyReceivePhase();
            List<Player> receiveOrder = new ArrayList<>();
            receiveOrder.add(inFrontOfWhom);
            // TODO 需要考虑以接收第三张黑色情报的顺序判断死亡顺序
            return new ResolveResult(new ReceivePhaseSenderSkill(whoseTurn, messageCard, receiveOrder, inFrontOfWhom), true);
        }
        return new ResolveResult(new NextTurn(whoseTurn), true);
    }
}
