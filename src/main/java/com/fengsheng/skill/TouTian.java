package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.JieHuo;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 鄭文先技能【偷天】：争夺阶段你可以翻开此角色牌，然后视为你使用了一张【截获】。
 */
public class TouTian extends AbstractSkill {
    private static final Logger log = Logger.getLogger(TouTian.class);

    @Override
    public void init(Game g) {

    }

    @Override
    public SkillId getSkillId() {
        return SkillId.TOU_TIAN;
    }

    @Override
    public ResolveResult execute(Game g) {
        return null;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!JieHuo.canUse(g, r)) return;
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[偷天]");
            return;
        }
        var pb = (Role.skill_tou_tian_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.playerSetRoleFaceUp(r, true);
        log.info(r + "发动了[偷天]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_tou_tian_toc.newBuilder().setPlayerId(player.getAlternativeLocation(r.location())).build());
        }
        JieHuo.execute(null, g, r);
    }

    public static boolean ai(FightPhaseIdle e, final Skill skill) {
        if (e.whoseFightTurn.isRoleFaceUp())
            return false;
        Player player = e.whoseFightTurn;
        var colors = e.messageCard.getColors();
        if (e.inFrontOfWhom == player || (e.isMessageCardFaceUp || player == e.whoseTurn) && colors.size() == 1 && colors.get(0) == Common.color.Black)
            return false;
        if (ThreadLocalRandom.current().nextBoolean())
            return false;
        GameExecutor.post(e.whoseFightTurn.getGame(), () -> skill.executeProtocol(
                e.whoseFightTurn.getGame(), e.whoseFightTurn, Role.skill_tou_tian_tos.getDefaultInstance()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
