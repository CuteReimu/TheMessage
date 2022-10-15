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
 * 李宁玉技能【就计】：你被【试探】【威逼】或【利诱】指定为目标后，你可以翻开此角色牌，然后摸两张牌，并在触发此技能的卡牌结算后，将其加入你的手牌。
 */
public class JiuJi extends AbstractSkill implements TriggeredSkill {
    private static final Logger log = Logger.getLogger(JiuJi.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.JIU_JI;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof OnUseCard fsm) || fsm.askWhom.findSkill(getSkillId()) == null || !fsm.askWhom.isAlive())
            return null;
        if (fsm.cardType != Common.card_type.Shi_Tan && fsm.cardType != Common.card_type.Wei_Bi && fsm.cardType != Common.card_type.Li_You)
            return null;
        if (!fsm.targetPlayer.equals(fsm.askWhom))
            return null;
        if (fsm.targetPlayer.isRoleFaceUp())
            return null;
        fsm.askWhom.addSkillUseCount(getSkillId());
        log.info(fsm.askWhom + "发动了[就计]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_jiu_ji_a_toc.newBuilder().setPlayerId(player.getAlternativeLocation(fsm.askWhom.location())).build());
        }
        g.playerSetRoleFaceUp(fsm.askWhom, true);
        fsm.askWhom.draw(2);
        return null;
    }
}
