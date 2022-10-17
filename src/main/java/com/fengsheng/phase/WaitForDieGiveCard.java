package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;

import java.util.List;

/**
 * 等待死亡角色给三张牌
 */
public class WaitForDieGiveCard implements Fsm {
    /**
     * 谁的回合
     */
    public Player whoseTurn;
    /**
     * 结算到dieQueue的第几个人的死亡给三张牌了
     */
    public int diedIndex;
    /**
     * 死亡的顺序
     */
    public List<Player> diedQueue;
    /**
     * 在结算死亡技能时，又有新的人获得三张黑色情报的顺序
     */
    public ReceiveOrder receiveOrder;
    /**
     * 死亡结算后的下一个动作
     */
    public Fsm afterDieResolve;

    public WaitForDieGiveCard(Player whoseTurn, List<Player> diedQueue, ReceiveOrder receiveOrder, Fsm afterDieResolve) {
        this.whoseTurn = whoseTurn;
        this.diedQueue = diedQueue;
        this.receiveOrder = receiveOrder;
        this.afterDieResolve = afterDieResolve;
    }

    @Override
    public ResolveResult resolve() {
        if (diedIndex >= diedQueue.size())
            return new ResolveResult(new CheckWin(whoseTurn, receiveOrder, afterDieResolve), true);
        Player whoDie = diedQueue.get(diedIndex);
        if (whoDie.getCards().isEmpty())
            return new ResolveResult(new AfterDieGiveCard(this), true);
        for (Player p : whoDie.getGame().getPlayers()) {
            p.waitForDieGiveCard(whoDie, 20);
        }
        return null;
    }
}
