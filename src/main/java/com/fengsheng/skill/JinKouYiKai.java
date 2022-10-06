package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 玄青子技能【金口一开】：你的回合的争夺阶段限一次，你可以查看牌堆顶的一张牌，然后选择一项：
 * <ul><li>你摸一张牌。</li><li>将牌堆顶的一张牌和待接收情报面朝下互换</li></ul>
 */
public class JinKouYiKai extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(JinKouYiKai.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.JIN_KOU_YI_KAI;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn || r != fsm.whoseTurn) {
            log.error("现在不是发动[金口一开]的时机");
            return;
        }
        if (r.getSkillUseCount(getSkillId()) > 0) {
            log.error("[金口一开]一回合只能发动一次");
            return;
        }
        var pb = (Role.skill_jin_kou_yi_kai_a_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        List<Card> cards = g.getDeck().peek(1);
        if (cards.isEmpty()) {
            log.error("牌堆没牌了");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.resolve(new executeJinKouYiKai(fsm, r, cards));
    }

    private record executeJinKouYiKai(FightPhaseIdle fsm, Player r, List<Card> cards) implements WaitingFsm {
        @Override
        public ResolveResult resolve() {
            final Game g = r.getGame();
            log.info(r + "发动了[金口一开]");
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_jin_kou_yi_kai_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(20);
                    if (player == r) {
                        builder.setCard(cards.get(0).toPbCard());
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, Role.skill_jin_kou_yi_kai_b_tos.newBuilder()
                                        .setExchange(false).setSeq(seq2).build());
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> g.tryContinueResolveProtocol(r, Role.skill_jin_kou_yi_kai_b_tos.newBuilder()
                        .setExchange(ThreadLocalRandom.current().nextBoolean()).build()), 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != r) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_jin_kou_yi_kai_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            r.incrSeq();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_jin_kou_yi_kai_b_toc.newBuilder().setExchange(pb.getExchange())
                            .setPlayerId(player1.getAlternativeLocation(r.location())).build());
            }
            if (pb.getExchange()) {
                Card temp = cards.get(0);
                log.info(r + "将待接收情报" + fsm.messageCard + "与牌堆顶的" + temp + "互换");
                cards.set(0, fsm.messageCard);
                fsm.messageCard = temp;
                fsm.isMessageCardFaceUp = false;
            } else {
                r.draw(1);
            }
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        Player player = e.whoseFightTurn;
        if (player != e.whoseTurn || player.getSkillUseCount(SkillId.JIN_KOU_YI_KAI) > 0)
            return false;
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_jin_kou_yi_kai_a_tos.getDefaultInstance()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
