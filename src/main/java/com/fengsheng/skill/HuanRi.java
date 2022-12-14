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
    public SkillId getSkillId() {
        return SkillId.HUAN_RI;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof OnUseCard fsm) || fsm.askWhom.findSkill(getSkillId()) == null || !fsm.askWhom.isAlive())
            return null;
        if (fsm.player != fsm.askWhom)
            return null;
        if (fsm.cardType != Common.card_type.Diao_Bao && fsm.cardType != Common.card_type.Po_Yi)
            return null;
        if (!fsm.player.isRoleFaceUp())
            return null;
        fsm.askWhom.addSkillUseCount(getSkillId());
        log.info(fsm.askWhom + "发动了[换日]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_huan_ri_toc.newBuilder().setPlayerId(player.getAlternativeLocation(fsm.askWhom.location())).build());
        }
        g.playerSetRoleFaceUp(fsm.askWhom, false);
        return null;
    }
}
