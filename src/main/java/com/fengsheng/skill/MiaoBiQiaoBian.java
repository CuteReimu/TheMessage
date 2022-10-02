package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.card.PlayerAndCard;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 连鸢技能【妙笔巧辩】：争夺阶段，你可以翻开此角色牌，然后从所有角色的情报区选择合计至多两张不含有相同颜色的情报，将其加入你的手牌。
 */
public class MiaoBiQiaoBian extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(MiaoBiQiaoBian.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.MIAO_BI_QIAO_BIAN;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("现在不是发动[妙笔巧辩]的时机");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[妙笔巧辩]");
            return;
        }
        var pb = (Role.skill_miao_bi_qiao_bian_a_tos) message;
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
        Card card = target.findMessageCard(pb.getCardId());
        if (card == null) {
            log.error("没有这张牌");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.playerSetRoleFaceUp(r, true);
        g.resolve(new excuteMiaoBiQiaoBian(fsm, r, target, card));
    }

    private record excuteMiaoBiQiaoBian(FightPhaseIdle fsm, Player r, Player target1,
                                        Card card1) implements WaitingFsm {
        @Override
        public ResolveResult resolve() {
            final Game g = r.getGame();
            target1.deleteMessageCard(card1.getId());
            r.addCard(card1);
            log.info(r + "发动了[妙笔巧辩]，拿走了" + target1 + "面前的" + card1);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_miao_bi_qiao_bian_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player.getAlternativeLocation(target1.location()));
                    builder.setCardId(card1.getId());
                    builder.setWaitingSecond(20);
                    if (player == r) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, Role.skill_miao_bi_qiao_bian_b_tos.newBuilder()
                                        .setEnable(false).setSeq(seq2).build());
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    List<PlayerAndCard> playerAndCards = new ArrayList<>();
                    for (Player p : g.getPlayers()) {
                        if (p.isAlive()) {
                            for (Card c : p.getMessageCards().values()) {
                                if (!card1.hasSameColor(c))
                                    playerAndCards.add(new PlayerAndCard(p, c));
                            }
                        }
                    }
                    if (playerAndCards.isEmpty()) {
                        g.tryContinueResolveProtocol(r, Role.skill_miao_bi_qiao_bian_b_tos.newBuilder().setEnable(false).build());
                    } else {
                        final PlayerAndCard playerAndCard = playerAndCards.get(ThreadLocalRandom.current().nextInt(playerAndCards.size()));
                        g.tryContinueResolveProtocol(r, Role.skill_miao_bi_qiao_bian_b_tos.newBuilder().setEnable(true)
                                .setTargetPlayerId(r.getAlternativeLocation(playerAndCard.player().location()))
                                .setCardId(playerAndCard.card().getId()).build());
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
            if (!(message instanceof Role.skill_miao_bi_qiao_bian_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (!pb.getEnable()) {
                r.incrSeq();
                return new ResolveResult(fsm, true);
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
            Card card2 = target2.findMessageCard(pb.getCardId());
            if (card2 == null) {
                log.error("没有这张牌");
                return null;
            }
            if (card2.hasSameColor(card1)) {
                log.error("两张牌含有相同颜色");
                return null;
            }
            r.incrSeq();
            log.info(r + "拿走了" + target2 + "面前的" + card2);
            target2.deleteMessageCard(card2.getId());
            r.addCard(card2);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_miao_bi_qiao_bian_b_toc.newBuilder().setCardId(card2.getId())
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(player1.getAlternativeLocation(target2.location())).build());
            }
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        Player player = e.whoseFightTurn;
        if (player.isRoleFaceUp())
            return false;
        int playerCount = player.getGame().getPlayers().length;
        List<PlayerAndCard> playerAndCards = new ArrayList<>();
        for (Player p : player.getGame().getPlayers()) {
            for (Card c : p.getCards().values())
                playerAndCards.add(new PlayerAndCard(p, c));
        }
        if (playerAndCards.size() < playerCount) return false;
        if (ThreadLocalRandom.current().nextInt(playerCount * playerCount) != 0)
            return false;
        PlayerAndCard playerAndCard = playerAndCards.get(ThreadLocalRandom.current().nextInt(playerAndCards.size()));
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_miao_bi_qiao_bian_a_tos.newBuilder().setCardId(playerAndCard.card().getId())
                        .setTargetPlayerId(player.getAlternativeLocation(playerAndCard.player().location())).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
