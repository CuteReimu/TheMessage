package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 阿芙罗拉技能【妙手】：争夺阶段，你可以翻开此角色牌，然后弃置待接收情报，并查看一名角色的手牌和情报区，从中选择一张牌作为待收情报，面朝上移至一名角色的面前。
 */
public class MiaoShou extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(MiaoShou.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.MIAO_SHOU;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("现在不是发动[妙手]的时机");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[妙手]");
            return;
        }
        var pb = (Role.skill_miao_shou_a_tos) message;
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
        if (target.getCards().isEmpty() && target.getMessageCards().isEmpty()) {
            log.error("目标没有手牌，也没有情报牌");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.playerSetRoleFaceUp(r, true);
        g.getDeck().discard(fsm.messageCard);
        g.resolve(new executeMiaoShou(fsm, r, target));
    }

    private record executeMiaoShou(FightPhaseIdle fsm, Player r, Player target) implements WaitingFsm {
        @Override
        public ResolveResult resolve() {
            final Game g = r.getGame();
            log.info(r + "对" + target + "发动了[妙手]");
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_miao_shou_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player.getAlternativeLocation(target.location()));
                    builder.setWaitingSecond(20);
                    if (player == r) {
                        for (Card card : target.getCards().values())
                            builder.addCards(card.toPbCard());
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2)) {
                                for (Card card : target.getCards().values()) {
                                    g.tryContinueResolveProtocol(r, Role.skill_miao_shou_b_tos.newBuilder()
                                            .setCardId(card.getId()).setTargetPlayerId(0).setSeq(seq2).build());
                                    return;
                                }
                                for (Card card : target.getMessageCards().values()) {
                                    g.tryContinueResolveProtocol(r, Role.skill_miao_shou_b_tos.newBuilder()
                                            .setMessageCardId(card.getId()).setTargetPlayerId(0).setSeq(seq2).build());
                                    return;
                                }
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    for (Card card : target.getCards().values()) {
                        g.tryContinueResolveProtocol(r, Role.skill_miao_shou_b_tos.newBuilder()
                                .setCardId(card.getId()).setTargetPlayerId(0).build());
                        return;
                    }
                    for (Card card : target.getMessageCards().values()) {
                        g.tryContinueResolveProtocol(r, Role.skill_miao_shou_b_tos.newBuilder()
                                .setMessageCardId(card.getId()).setTargetPlayerId(0).build());
                        return;
                    }
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
            if (!(message instanceof Role.skill_miao_shou_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (pb.getCardId() != 0 && pb.getMessageCardId() != 0) {
                log.error("只能选择手牌或情报其中之一");
                return null;
            }
            if (pb.getTargetPlayerId() < 0 || pb.getTargetPlayerId() >= g.getPlayers().length) {
                log.error("目标错误");
                return null;
            }
            Player target2 = g.getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
            if (!target2.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            Card card;
            if (pb.getCardId() == 0 && pb.getMessageCardId() == 0) {
                log.error("必须选择一张手牌或情报");
                return null;
            } else if (pb.getMessageCardId() == 0) {
                card = target.deleteCard(pb.getCardId());
                if (card == null) {
                    log.error("没有这张牌");
                    return null;
                }
            } else {
                card = target.deleteMessageCard(pb.getMessageCardId());
                if (card == null) {
                    log.error("没有这张牌");
                    return null;
                }
            }
            r.incrSeq();
            log.info(r + "将" + card + "作为情报，面朝上移至" + target2 + "的面前");
            fsm.messageCard = card;
            fsm.inFrontOfWhom = target2;
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            fsm.isMessageCardFaceUp = true;
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_miao_shou_b_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setFromPlayerId(player1.getAlternativeLocation(target.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target2.location()));
                    if (pb.getCardId() != 0) builder.setCard(card.toPbCard());
                    else builder.setMessageCardId(card.getId());
                    player1.send(builder.build());
                }
            }
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        Player player = e.whoseFightTurn;
        if (player.isRoleFaceUp())
            return false;
        List<Player> players = new ArrayList<>();
        for (Player p : player.getGame().getPlayers()) {
            if (p.isAlive() && (!p.getCards().isEmpty() || !p.getMessageCards().isEmpty())) {
                players.add(p);
            }
        }
        if (players.isEmpty()) return false;
        if (ThreadLocalRandom.current().nextInt(players.size()) != 0)
            return false;
        Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_miao_shou_a_tos.newBuilder().setTargetPlayerId(p.location()).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
