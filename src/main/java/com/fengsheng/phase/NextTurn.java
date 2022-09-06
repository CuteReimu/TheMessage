package com.fengsheng.phase;

import com.fengsheng.Fsm;
import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;

/**
 * 即将跳转到下一回合时
 *
 * @param player 当前回合的玩家（不是下回合的玩家）
 */
public record NextTurn(Player player) implements Fsm {
    @Override
    public ResolveResult resolve() {
        Game game = this.player.getGame();
        int whoseTurn = this.player.location();
        while (true) {
            whoseTurn = (whoseTurn + 1) % game.getPlayers().length;
            Player player = game.getPlayers()[whoseTurn];
            if (player.isAlive()) {
                for (Player p : game.getPlayers()) {
                    p.resetSkillUseCount();
                }
                return new ResolveResult(new DrawPhase(player), true);
            }
        }
    }
}
