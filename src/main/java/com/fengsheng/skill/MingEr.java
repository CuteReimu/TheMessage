package com.fengsheng.skill;

import com.fengsheng.Game;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.phase.ReceivePhaseSenderSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import org.apache.log4j.Logger;

/**
 * 老鳖技能【明饵】：你传出的红色或蓝色情报被接收后，你和接收者各摸一张牌。
 */
public class MingEr extends AbstractSkill implements TriggeredSkill {
    private static final Logger log = Logger.getLogger(MingEr.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.MING_ER;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof ReceivePhaseSenderSkill fsm) || fsm.whoseTurn.findSkill(getSkillId()) == null || !fsm.whoseTurn.isAlive())
            return null;
        if (fsm.whoseTurn.getSkillUseCount(getSkillId()) > 0)
            return null;
        var colors = fsm.messageCard.getColors();
        if (!colors.contains(Common.color.Red) && !colors.contains(Common.color.Blue))
            return null;
        fsm.whoseTurn.addSkillUseCount(getSkillId());
        log.info(fsm.whoseTurn + "发动了[明饵]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_ming_er_toc.newBuilder().setPlayerId(player.getAlternativeLocation(fsm.whoseTurn.location())).build());
        }
        if (fsm.whoseTurn == fsm.inFrontOfWhom) {
            fsm.whoseTurn.draw(2);
        } else {
            fsm.whoseTurn.draw(1);
            fsm.inFrontOfWhom.draw(1);
        }
        return null;
    }
}
