package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import org.apache.log4j.Logger;

import java.util.LinkedList;
import java.util.List;

public class WaitForChengQing implements Fsm {
    private static final Logger log = Logger.getLogger(WaitForChengQing.class);

    /**
     * 谁的回合
     */
    public Player whoseTurn;
    /**
     * 正在结算谁的濒死
     */
    public Player whoDie;
    /**
     * 正在结算谁是否使用澄清
     */
    public Player askWhom;
    /**
     * 结算濒死的顺序
     */
    public LinkedList<Player> dyingQueue;
    /**
     * 死亡的顺序
     */
    public List<Player> diedQueue;
    /**
     * 濒死结算后的下一个动作
     */
    public Fsm afterDieResolve;

    public WaitForChengQing(Player whoseTurn, Player whoDie, Player askWhom, LinkedList<Player> dyingQueue, List<Player> diedQueue, Fsm afterDieResolve) {
        this.whoseTurn = whoseTurn;
        this.whoDie = whoDie;
        this.askWhom = askWhom;
        this.dyingQueue = dyingQueue;
        this.diedQueue = diedQueue;
        this.afterDieResolve = afterDieResolve;
    }

    @Override
    public ResolveResult resolve() {
        log.info("正在询问" + askWhom + "是否使用澄清");
        for (Player p : askWhom.getGame().getPlayers()) {
            p.notifyAskForChengQing(whoDie, askWhom);
        }
        return new ResolveResult(this, false);
    }

    @Override
    public String toString() {
        return whoseTurn + "的回合，" + whoDie + "濒死，向" + askWhom + "求澄清";
    }
}
