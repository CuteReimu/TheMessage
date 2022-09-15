package com.fengsheng.skill;

import com.fengsheng.Game;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import org.apache.log4j.Logger;

/**
 * 鄭文先技能【换日】：你使用【调包】或【破译】后，可以将你的角色牌翻至面朝下。
 */
public class HuanRi extends AbstractSkill implements TriggeredSkill {
    private static final Logger log = Logger.getLogger(HuanRi.class);

    @Override
    public void init(Game g) {
        g.addListeningSkill(this);
    }

    @Override
    public SkillId getSkillId() {
        return SkillId.HUAN_RI;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof OnUseCard fsm) || fsm.askWhom.findSkill(getSkillId()) == null || !fsm.askWhom.isAlive())
            return null;
        if (!fsm.player.equals(fsm.askWhom))
            return null;
        if (fsm.card.getType() != Common.card_type.Diao_Bao && fsm.card.getType() != Common.card_type.Po_Yi)
            return null;
        if (!fsm.player.isRoleFaceUp())
            return null;
        fsm.whoseTurn.addSkillUseCount(getSkillId());
        log.info(fsm.whoseTurn + "发动了[换日]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_huan_ri_toc.newBuilder().setPlayerId(player.getAlternativeLocation(fsm.whoseTurn.location())).build());
        }
        g.playerSetRoleFaceUp(fsm.whoseTurn, false);
        return null;
    }
}
