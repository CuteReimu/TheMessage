package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 判断是否有人胜利
 * <li>只有接收阶段正常接收情报才会进入 {@link ReceivePhaseSenderSkill} </li>
 * <li>其它情况均为置入情报区，一律进入这里。</li>
 */
public class CheckWin implements Fsm {
    private static final Logger log = Logger.getLogger(CheckWin.class);

    /**
     * 谁的回合
     */
    public Player whoseTurn;
    /**
     * 接收第三张黑色情报的顺序
     */
    public final ReceiveOrder receiveOrder;
    /**
     * 濒死结算后的下一个动作
     */
    public Fsm afterDieResolve;

    public CheckWin(Player whoseTurn, Fsm afterDieResolve) {
        this(whoseTurn, new ReceiveOrder(), afterDieResolve);
    }

    public CheckWin(Player whoseTurn, ReceiveOrder receiveOrder, Fsm afterDieResolve) {
        this.whoseTurn = whoseTurn;
        this.receiveOrder = receiveOrder;
        this.afterDieResolve = afterDieResolve;
    }

    @Override
    public ResolveResult resolve() {
        Game game = whoseTurn.getGame();
        Player stealer = null; // 簒夺者
        List<Player> redPlayers = new ArrayList<>(), bluePlayers = new ArrayList<>();
        for (Player p : game.getPlayers()) {
            if (p.isLose()) continue;
            switch (p.getIdentity()) {
                case Black -> {
                    if (p.getSecretTask() == Common.secret_task.Stealer) stealer = p;
                }
                case Red -> redPlayers.add(p);
                case Blue -> bluePlayers.add(p);
            }
        }
        List<Player> declareWinner = new ArrayList<>(), winner = new ArrayList<>();
        boolean redWin = false, blueWin = false;
        for (Player player : game.getPlayers()) {
            if (player.isLose()) continue;
            int red = 0, blue = 0;
            for (Card card : player.getMessageCards().values()) {
                for (Common.color color : card.getColors()) {
                    switch (color) {
                        case Red -> red++;
                        case Blue -> blue++;
                    }
                }
            }
            switch (player.getIdentity()) {
                case Black:
                    if (player.getSecretTask() == Common.secret_task.Collector && (red >= 3 || blue >= 3)) {
                        declareWinner.add(player);
                        winner.add(player);
                    }
                    break;
                case Red:
                    if (red >= 3) {
                        declareWinner.add(player);
                        redWin = true;
                    }
                    break;
                case Blue:
                    if (blue >= 3) {
                        declareWinner.add(player);
                        blueWin = true;
                    }
            }
        }
        if (redWin) winner.addAll(redPlayers);
        if (blueWin) winner.addAll(bluePlayers);
        if (!declareWinner.isEmpty() && stealer != null && stealer.equals(whoseTurn)) {
            declareWinner = List.of(stealer);
            winner = new ArrayList<>(declareWinner);
        }
        if (!declareWinner.isEmpty()) {
            boolean hasGuXiaoMeng = false;
            for (Player p : winner) {
                if (p.findSkill(SkillId.WEI_SHENG) != null && p.isRoleFaceUp()) {
                    hasGuXiaoMeng = true;
                    break;
                }
            }
            if (hasGuXiaoMeng) {
                for (Player p : game.getPlayers()) {
                    if (!p.isLose() && p.getIdentity() == Common.color.Has_No_Identity) {
                        winner.add(p);
                    }
                }
            }
            var declareWinners = declareWinner.toArray(new Player[0]);
            var winners = winner.toArray(new Player[0]);
            log.info(Arrays.toString(declareWinners) + "宣告胜利，胜利者有" + Arrays.toString(winners));
            for (Player p : game.getPlayers())
                p.notifyWin(declareWinners, winners);
            whoseTurn.getGame().end();
            return new ResolveResult(null, false);
        }
        return new ResolveResult(new StartWaitForChengQing(whoseTurn, receiveOrder, afterDieResolve), true);
    }
}
