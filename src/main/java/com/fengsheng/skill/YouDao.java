package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import org.apache.log4j.Logger;

/**
 * SP李宁玉技能【诱导】：你使用【误导】后，摸一张牌。
 */
public class YouDao extends AbstractSkill implements TriggeredSkill {
    private static final Logger log = Logger.getLogger(YouDao.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.YOU_DAO;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof OnUseCard fsm) || fsm.askWhom != fsm.player || fsm.askWhom.findSkill(getSkillId()) == null)
            return null;
        if (fsm.cardType != Common.card_type.Wu_Dao)
            return null;
        if (fsm.askWhom.getSkillUseCount(getSkillId()) > 0)
            return null;
        fsm.askWhom.addSkillUseCount(getSkillId());
        final Player r = fsm.askWhom;
        log.info(r + "发动了[诱导]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_you_dao_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location())).build());
        }
        r.draw(1);
        final Fsm oldResolveFunc = fsm.resolveFunc;
        fsm.resolveFunc = () -> {
            r.resetSkillUseCount(getSkillId());
            return oldResolveFunc.resolve();
        };
        return new ResolveResult(fsm, true);
    }
}
