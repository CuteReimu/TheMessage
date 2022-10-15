package com.fengsheng.skill;

import com.fengsheng.Game;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.phase.ReceivePhaseReceiverSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import org.apache.log4j.Logger;

/**
 * 老汉技能【视死】：你接收黑色情报后，摸两张牌。
 */
public class ShiSi extends AbstractSkill implements TriggeredSkill {
    private static final Logger log = Logger.getLogger(ShiSi.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.SHI_SI;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof ReceivePhaseReceiverSkill fsm) || fsm.inFrontOfWhom().findSkill(getSkillId()) == null || !fsm.inFrontOfWhom().isAlive())
            return null;
        if (fsm.inFrontOfWhom().getSkillUseCount(getSkillId()) > 0)
            return null;
        var colors = fsm.messageCard().getColors();
        if (!colors.contains(Common.color.Black))
            return null;
        fsm.inFrontOfWhom().addSkillUseCount(getSkillId());
        log.info(fsm.inFrontOfWhom() + "发动了[视死]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_shi_si_toc.newBuilder().setPlayerId(player.getAlternativeLocation(fsm.inFrontOfWhom().location())).build());
        }
        fsm.inFrontOfWhom().draw(2);
        return null;
    }
}
