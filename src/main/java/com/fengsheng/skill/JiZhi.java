package com.fengsheng.skill;

import com.fengsheng.Game;
import com.fengsheng.GameExecutor;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.phase.WaitForChengQing;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * 顾小梦技能【集智】：一名角色濒死时，或争夺阶段，你可以翻开此角色牌，然后摸四张牌。
 */
public class JiZhi extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(JiZhi.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.JI_ZHI;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if ((!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn)
                && (!(g.getFsm() instanceof WaitForChengQing fsm2) || r != fsm2.askWhom)) {
            log.error("现在不是发动[急智]的时机");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("角色面朝上时不能发动[急智]");
            return;
        }
        var pb = (Role.skill_ji_zhi_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        log.info(r + "发动了[急智]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_ji_zhi_toc.newBuilder().setPlayerId(player.getAlternativeLocation(r.location())).build());
        }
        g.playerSetRoleFaceUp(r, true);
        r.draw(4);
        g.continueResolve();
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        if (e.whoseFightTurn.isRoleFaceUp())
            return false;
        GameExecutor.post(e.whoseFightTurn.getGame(), () -> skill.executeProtocol(
                e.whoseFightTurn.getGame(), e.whoseFightTurn, Role.skill_ji_zhi_tos.getDefaultInstance()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
