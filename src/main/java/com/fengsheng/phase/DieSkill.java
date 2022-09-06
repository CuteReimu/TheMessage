package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;

import java.util.List;

/**
 * 死亡时的技能结算
 */
public class DieSkill implements Fsm {
    /**
     * 谁的回合
     */
    public Player whoseTurn;
    /**
     * 结算到dieQueue的第几个人的死亡事件了
     */
    public int diedIndex;
    /**
     * 死亡的顺序
     */
    public List<Player> diedQueue;
    /**
     * 正在询问谁
     */
    public Player askWhom;
    /**
     * 在结算死亡技能时，又有新的人获得三张黑色情报的顺序
     */
    public ReceiveOrder receiveOrder = new ReceiveOrder();
    /**
     * 死亡结算后的下一个动作
     */
    public Fsm afterDieResolve;

    public DieSkill(Player whoseTurn, List<Player> diedQueue, Player askWhom, Fsm afterDieResolve) {
        this.whoseTurn = whoseTurn;
        this.diedQueue = diedQueue;
        this.askWhom = askWhom;
        this.afterDieResolve = afterDieResolve;
    }

    @Override
    public ResolveResult resolve() {
        ResolveResult result = askWhom.getGame().dealListeningSkill();
        return result != null ? result : new ResolveResult(new DieSkillNext(this), true);
    }

    /**
     * 进行下一个玩家死亡时的技能结算
     */
    private record DieSkillNext(DieSkill dieSkill) implements Fsm {
        @Override
        public ResolveResult resolve() {
            Player[] players = dieSkill.whoseTurn.getGame().getPlayers();
            int askWhom = dieSkill.askWhom.location();
            while (true) {
                askWhom = (askWhom + 1) % players.length;
                if (askWhom == dieSkill.diedQueue.get(dieSkill.diedIndex).location()) {
                    dieSkill.diedIndex++;
                    if (dieSkill.diedIndex >= dieSkill.diedQueue.size())
                        return new ResolveResult(new WaitForDieGiveCard(dieSkill.whoseTurn, dieSkill.diedQueue, dieSkill.receiveOrder, dieSkill.afterDieResolve), true);
                    dieSkill.askWhom = dieSkill.diedQueue.get(dieSkill.diedIndex);
                    return new ResolveResult(dieSkill, true);
                }
                if (players[askWhom].isAlive()) {
                    dieSkill.askWhom = players[askWhom];
                    return new ResolveResult(dieSkill, true);
                }
            }
        }
    }
}
