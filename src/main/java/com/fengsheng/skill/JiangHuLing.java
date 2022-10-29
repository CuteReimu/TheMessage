package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.OnSendCard;
import com.fengsheng.phase.ReceivePhaseSenderSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 王富贵技能【江湖令】：你传出情报后，可以宣言一个颜色。本回合中，当情报被接收后，你可以从接收者的情报区弃置一张被宣言颜色的情报，若弃置的是黑色情报，则你摸一张牌。
 */
public class JiangHuLing implements TriggeredSkill {
    @Override
    public SkillId getSkillId() {
        return SkillId.JIANG_HU_LING;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (g.getFsm() instanceof OnSendCard fsm) {
            Player r = fsm.whoseTurn();
            if (r.findSkill(getSkillId()) == null)
                return null;
            if (r.getSkillUseCount(getSkillId()) >= 1)
                return null;
            r.addSkillUseCount(getSkillId());
            return new ResolveResult(new executeJiangHuLingA(fsm, r), true);
        }
        return null;
    }

    private record executeJiangHuLingA(Fsm fsm, Player r) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJiangHuLingA.class);

        @Override
        public ResolveResult resolve() {
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Role.skill_wait_for_jiang_hu_ling_a_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(15);
                    if (p == r) {
                        final int seq2 = p.getSeq();
                        builder.setSeq(seq2);
                        GameExecutor.post(p.getGame(), () -> {
                            if (p.checkSeq(seq2))
                                p.getGame().tryContinueResolveProtocol(p, Role.skill_jiang_hu_ling_a_tos.newBuilder().setEnable(false).setSeq(seq2).build());
                        }, p.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS);
                    }
                    p.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(r.getGame(), () -> {
                    List<Common.color> colors = List.of(Common.color.Black, Common.color.Red, Common.color.Blue);
                    Common.color color = colors.get(ThreadLocalRandom.current().nextInt(colors.size()));
                    r.getGame().tryContinueResolveProtocol(r, Role.skill_jiang_hu_ling_a_tos.newBuilder().setEnable(true).setColor(color).build());
                }, 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != r) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_jiang_hu_ling_a_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (!pb.getEnable()) {
                r.incrSeq();
                return new ResolveResult(fsm, true);
            }
            if (pb.getColor() == Common.color.UNRECOGNIZED) {
                log.error("未知的颜色类型");
                return null;
            }
            r.incrSeq();
            Skill[] skills = r.getSkills();
            Skill[] skills2 = new Skill[skills.length + 1];
            System.arraycopy(skills, 0, skills2, 0, skills.length);
            skills2[skills2.length - 1] = new JiangHuLing2(pb.getColor());
            r.setSkills(skills2);
            log.info(r + "发动了[江湖令]，宣言了" + pb.getColor());
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_jiang_hu_ling_a_toc.newBuilder()
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setColor(pb.getColor()).build());
            }
            return new ResolveResult(fsm, true);
        }
    }

    private record JiangHuLing2(Common.color color) implements TriggeredSkill {
        @Override
        public void init(Game g) {
            // Do nothing
        }

        @Override
        public SkillId getSkillId() {
            return SkillId.JIANG_HU_LING2;
        }

        @Override
        public ResolveResult execute(Game g) {
            if (g.getFsm() instanceof ReceivePhaseSenderSkill fsm) {
                Player r = fsm.whoseTurn;
                if (r.findSkill(getSkillId()) == null)
                    return null;
                if (r.getSkillUseCount(getSkillId()) >= 1)
                    return null;
                boolean containsColor = false;
                for (Card card : fsm.inFrontOfWhom.getMessageCards().values()) {
                    if (card.getColors().contains(color)) {
                        containsColor = true;
                        break;
                    }
                }
                if (!containsColor)
                    return null;
                r.addSkillUseCount(getSkillId());
                return new ResolveResult(new executeJiangHuLingB(fsm, color), true);
            }
            return null;
        }
    }

    private record executeJiangHuLingB(ReceivePhaseSenderSkill fsm, Common.color color) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJiangHuLingB.class);

        @Override
        public ResolveResult resolve() {
            for (Player p : fsm.whoseTurn.getGame().getPlayers())
                p.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.whoseTurn, 15);
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.whoseTurn) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (message instanceof Fengsheng.end_receive_phase_tos pb) {
                if (player instanceof HumanPlayer r && !r.checkSeq(pb.getSeq())) {
                    log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
                    return null;
                }
                player.incrSeq();
                return new ResolveResult(fsm, true);
            }
            if (!(message instanceof Role.skill_jiang_hu_ling_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.whoseTurn;
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            Player target = fsm.inFrontOfWhom;
            if (!target.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            Card card = target.findMessageCard(pb.getCardId());
            if (card == null) {
                log.error("没有这张卡");
                return null;
            }
            if (!card.getColors().contains(color)) {
                log.error("你选择的情报不是宣言的颜色");
                return null;
            }
            r.incrSeq();
            log.info(r + "发动了[江湖令]，弃掉了" + target + "面前的" + card);
            target.deleteMessageCard(card.getId());
            r.getGame().getDeck().discard(card);
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_jiang_hu_ling_b_toc.newBuilder().setCardId(card.getId())
                            .setPlayerId(player1.getAlternativeLocation(r.location())).build());
            }
            if (card.getColors().contains(Common.color.Black))
                r.draw(1);
            return new ResolveResult(fsm, true);
        }
    }

    public static void resetJiangHuLing(Game game) {
        for (Player p : game.getPlayers()) {
            Skill[] skills = p.getSkills();
            boolean containsJiangHuLing = false;
            for (Skill skill : skills) {
                if (skill.getSkillId() == SkillId.JIANG_HU_LING2) {
                    containsJiangHuLing = true;
                    break;
                }
            }
            if (containsJiangHuLing) {
                List<Skill> skills2 = new ArrayList<>(skills.length - 1);
                for (Skill skill : skills) {
                    if (skill.getSkillId() != SkillId.JIANG_HU_LING2)
                        skills2.add(skill);
                }
                p.setSkills(skills2.toArray(new Skill[0]));
            }
        }
    }

    public static boolean ai(Fsm fsm0) {
        if (!(fsm0 instanceof executeJiangHuLingB fsm))
            return false;
        Player p = fsm.fsm().whoseTurn;
        Player target = fsm.fsm().inFrontOfWhom;
        if (!target.isAlive()) return false;
        for (Card card : target.getMessageCards().values()) {
            if (card.getColors().contains(fsm.color) && !(
                    p == target && card.getColors().size() == 1 && card.getColors().get(0) != Common.color.Black)) {
                GameExecutor.post(p.getGame(), () -> p.getGame().tryContinueResolveProtocol(p, Role.skill_jiang_hu_ling_b_tos.newBuilder().setCardId(card.getId()).build()), 2, TimeUnit.SECONDS);
                return true;
            }
        }
        return false;
    }
}
