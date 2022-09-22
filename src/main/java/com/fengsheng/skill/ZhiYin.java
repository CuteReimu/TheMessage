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
 * 程小蝶技能【知音】：你接收红色或蓝色情报后，你和传出者各摸一张牌
 */
public class ZhiYin extends AbstractSkill implements TriggeredSkill {
    private static final Logger log = Logger.getLogger(ZhiYin.class);

    @Override
    public void init(Game g) {
        g.addListeningSkill(this);
    }

    @Override
    public SkillId getSkillId() {
        return SkillId.ZHI_YIN;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof ReceivePhaseReceiverSkill fsm) || fsm.inFrontOfWhom().findSkill(getSkillId()) == null || !fsm.inFrontOfWhom().isAlive())
            return null;
        if (fsm.inFrontOfWhom().getSkillUseCount(getSkillId()) > 0)
            return null;
        var colors = fsm.messageCard().getColors();
        if (!colors.contains(Common.color.Red) && !colors.contains(Common.color.Blue))
            return null;
        fsm.inFrontOfWhom().addSkillUseCount(getSkillId());
        log.info(fsm.inFrontOfWhom() + "发动了[知音]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_zhi_yin_toc.newBuilder().setPlayerId(player.getAlternativeLocation(fsm.inFrontOfWhom().location())).build());
        }
        if (fsm.inFrontOfWhom() == fsm.whoseTurn()) {
            fsm.inFrontOfWhom().draw(2);
        } else {
            fsm.inFrontOfWhom().draw(1);
            if (fsm.whoseTurn().isAlive()) fsm.whoseTurn().draw(1);
        }
        return null;
    }
}
