package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.DieSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * 顾小梦技能【承志】：一名其他角色死亡前，若此角色牌已翻开，则你获得其所有手牌，并查看其身份牌，你可以获得该身份牌，并将你原本的身份牌面朝下移出游戏。
 */
public class ChengZhi extends AbstractSkill implements TriggeredSkill {
    @Override
    public SkillId getSkillId() {
        return SkillId.CHENG_ZHI;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof DieSkill fsm) || fsm.askWhom == fsm.diedQueue.get(fsm.diedIndex) || fsm.askWhom.findSkill(getSkillId()) == null)
            return null;
        if (!fsm.askWhom.isAlive())
            return null;
        if (!fsm.askWhom.isRoleFaceUp())
            return null;
        if (fsm.askWhom.getSkillUseCount(getSkillId()) > 0)
            return null;
        fsm.askWhom.addSkillUseCount(getSkillId());
        return new ResolveResult(new executeChengZhi(fsm), true);
    }

    private record executeChengZhi(DieSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeChengZhi.class);

        @Override
        public ResolveResult resolve() {
            Player r = fsm.askWhom;
            Player whoDie = fsm.diedQueue.get(fsm.diedIndex);
            Card[] cards = whoDie.deleteAllCards();
            r.addCard(cards);
            log.info(r + "发动了[承志]，获得了" + whoDie + "的" + Arrays.toString(cards));
            if (whoDie.getIdentity() == Common.color.Has_No_Identity)
                return new ResolveResult(fsm, true);
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Role.skill_wait_for_cheng_zhi_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location())).setWaitingSecond(20);
                    builder.setDiePlayerId(p.getAlternativeLocation(whoDie.location()));
                    if (p == r) {
                        for (Card card : cards) builder.addCards(card.toPbCard());
                        builder.setIdentity(whoDie.getIdentity());
                        builder.setSecretTask(whoDie.getSecretTask());
                        final int seq2 = p.getSeq();
                        builder.setSeq(seq2);
                        p.setTimeout(GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_cheng_zhi_tos.newBuilder().setEnable(false).setSeq(seq2).build()), p.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    p.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer)
                GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_cheng_zhi_tos.newBuilder().setEnable(true).build()), 2, TimeUnit.SECONDS);
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.askWhom) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_cheng_zhi_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.askWhom;
            Player whoDie = fsm.diedQueue.get(fsm.diedIndex);
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (!pb.getEnable()) {
                r.incrSeq();
                for (Player p : g.getPlayers()) {
                    if (p instanceof HumanPlayer player1)
                        player1.send(Role.skill_cheng_zhi_toc.newBuilder().setEnable(false).setPlayerId(player1.getAlternativeLocation(r.location()))
                                .setDiePlayerId(player1.getAlternativeLocation(whoDie.location())).build());
                }
                return new ResolveResult(fsm, true);
            }
            r.incrSeq();
            r.setIdentity(whoDie.getIdentity());
            r.setSecretTask(whoDie.getSecretTask());
            whoDie.setIdentity(Common.color.Has_No_Identity);
            log.info(r + "获得了" + whoDie + "的身份牌");
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_cheng_zhi_toc.newBuilder().setEnable(true).setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setDiePlayerId(player1.getAlternativeLocation(whoDie.location())).build());
            }
            return new ResolveResult(fsm, true);
        }
    }
}
