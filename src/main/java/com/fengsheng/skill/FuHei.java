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
 * 白菲菲技能【腹黑】：你传出的黑色情报被接收后，你摸一张牌。
 */
public class FuHei extends AbstractSkill implements TriggeredSkill {
    private static final Logger log = Logger.getLogger(FuHei.class);

    @Override
    public void init(Game g) {
        g.addListeningSkill(this);
    }

    @Override
    public SkillId getSkillId() {
        return SkillId.FU_HEI;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof ReceivePhaseSenderSkill fsm) || fsm.whoseTurn.findSkill(getSkillId()) == null || !fsm.whoseTurn.isAlive())
            return null;
        if (fsm.whoseTurn.getSkillUseCount(getSkillId()) > 0)
            return null;
        var colors = fsm.messageCard.getColors();
        if (!colors.contains(Common.color.Black))
            return null;
        fsm.whoseTurn.addSkillUseCount(getSkillId());
        log.info(fsm.whoseTurn + "发动了[腹黑]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_fu_hei_toc.newBuilder().setPlayerId(player.getAlternativeLocation(fsm.whoseTurn.location())).build());
        }
        fsm.whoseTurn.draw(1);
        return null;
    }
}
