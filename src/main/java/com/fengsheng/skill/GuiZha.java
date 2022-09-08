package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.LiYou;
import com.fengsheng.card.WeiBi;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 肥原龙川技能【诡诈】：出牌阶段限一次，你可以指定一名角色，然后视为你对其使用了一张【威逼】或【利诱】。
 */
public class GuiZha extends AbstractSkill {
    private static final Logger log = Logger.getLogger(GuiZha.class);

    @Override
    public void init(Game g) {

    }

    @Override
    public SkillId getSkillId() {
        return SkillId.GUI_ZHA;
    }

    @Override
    public ResolveResult execute(Game g) {
        return null;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("现在不是出牌阶段空闲时点");
            return;
        }
        if (r.getSkillUseCount(getSkillId()) > 0) {
            log.error("[诡诈]一回合只能发动一次");
            return;
        }
        var pb = (Role.skill_gui_zha_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (pb.getTargetPlayerId() < 0 || pb.getTargetPlayerId() >= g.getPlayers().length) {
            log.error("目标错误");
            return;
        }
        Player target = g.getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
        if (!target.isAlive()) {
            log.error("目标已死亡");
            return;
        }
        if (pb.getCardType() == Common.card_type.Wei_Bi) {
            if (!WeiBi.canUse(g, r, target, pb.getWantType())) return;
        } else if (pb.getCardType() == Common.card_type.Li_You) {
            if (!LiYou.canUse(g, r, target)) return;
        } else {
            log.error("你只能视为使用了[威逼]或[利诱]：" + pb.getCardType());
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        log.info("[肥原龙川]对" + target + "发动了[诡诈]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_gui_zha_toc.newBuilder().setPlayerId(player.getAlternativeLocation(r.location()))
                        .setTargetPlayerId(player.getAlternativeLocation(target.location())).setCardType(pb.getCardType()).build());
        }
        if (pb.getCardType() == Common.card_type.Wei_Bi)
            WeiBi.execute(null, g, r, target, pb.getWantType());
        else if (pb.getCardType() == Common.card_type.Li_You)
            LiYou.execute(null, g, r, target);
    }

    public static boolean ai(MainPhaseIdle e, final Skill skill) {
        if (e.player().getSkillUseCount(SkillId.GUI_ZHA) > 0)
            return false;
        List<Player> players = new ArrayList<>();
        for (Player p : e.player().getGame().getPlayers())
            if (p.isAlive()) players.add(p);
        if (players.isEmpty()) return false;
        final Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        GameExecutor.post(e.player().getGame(), () -> skill.executeProtocol(
                e.player().getGame(), e.player(), Role.skill_gui_zha_tos.newBuilder().setCardType(Common.card_type.Li_You)
                        .setTargetPlayerId(e.player().getAlternativeLocation(p.location())).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
