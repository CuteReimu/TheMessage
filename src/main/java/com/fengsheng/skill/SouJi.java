package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.phase.NextTurn;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 李醒技能【搜辑】：争夺阶段，你可以翻开此角色牌，然后查看一名角色的手牌和待收情报，并且你可以选择其中任意张黑色牌，展示并加入你的手牌。
 */
public class SouJi extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(SouJi.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.SOU_JI;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("现在不是发动[搜辑]的时机");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[搜辑]");
            return;
        }
        var pb = (Role.skill_sou_ji_a_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (pb.getTargetPlayerId() < 0 || pb.getTargetPlayerId() >= g.getPlayers().length) {
            log.error("目标错误");
            return;
        }
        if (pb.getTargetPlayerId() == 0) {
            log.error("不能以自己为目标");
            return;
        }
        Player target = g.getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
        if (!target.isAlive()) {
            log.error("目标已死亡");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.playerSetRoleFaceUp(r, true);
        g.getDeck().discard(fsm.messageCard);
        g.resolve(new executeSouJi(fsm, r, target));
    }

    private record executeSouJi(FightPhaseIdle fsm, Player r, Player target) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeSouJi.class);

        @Override
        public ResolveResult resolve() {
            final Game g = r.getGame();
            log.info(r + "对" + target + "发动了[搜辑]");
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_sou_ji_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player.getAlternativeLocation(target.location()));
                    builder.setWaitingSecond(20);
                    if (player == r) {
                        for (Card card : target.getCards().values())
                            builder.addCards(card.toPbCard());
                        builder.setMessageCard(fsm.messageCard.toPbCard());
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2))
                                g.tryContinueResolveProtocol(r, Role.skill_sou_ji_b_tos.newBuilder().setSeq(seq2).build());
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    var builder = Role.skill_sou_ji_b_tos.newBuilder();
                    for (Card card : target.getCards().values()) {
                        if (card.getColors().contains(Common.color.Black))
                            builder.addCardIds(card.getId());
                    }
                    if (fsm.messageCard.getColors().contains(Common.color.Black))
                        builder.setMessageCard(true);
                    g.tryContinueResolveProtocol(r, builder.build());
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
            if (!(message instanceof Role.skill_sou_ji_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            Card[] cards = new Card[pb.getCardIdsCount()];
            for (int i = 0; i < cards.length; i++) {
                Card card = target.findCard(pb.getCardIds(i));
                if (card == null) {
                    log.error("没有这张牌");
                    return null;
                }
                if (!card.getColors().contains(Common.color.Black)) {
                    log.error("这张牌不是黑色的");
                    return null;
                }
                cards[i] = card;
            }
            if (pb.getMessageCard() && !fsm.messageCard.getColors().contains(Common.color.Black)) {
                log.error("待收情报不是黑色的");
                return null;
            }
            r.incrSeq();
            if (cards.length > 0) {
                log.info(r + "将" + target + "的" + Arrays.toString(cards) + "收归手牌");
                for (Card card : cards)
                    target.deleteCard(card.getId());
                r.addCard(cards);
            }
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_sou_ji_b_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    for (Card card : cards)
                        builder.addCards(card.toPbCard());
                    if (pb.getMessageCard())
                        builder.setMessageCard(fsm.messageCard.toPbCard());
                    player1.send(builder.build());
                }
            }
            if (pb.getMessageCard()) {
                log.info(r + "将待收情报" + fsm.messageCard + "收归手牌，回合结束");
                r.addCard(fsm.messageCard);
                return new ResolveResult(new NextTurn(fsm.whoseTurn), true);
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        Player player = e.whoseFightTurn;
        if (player.isRoleFaceUp())
            return false;
        List<Player> players = new ArrayList<>();
        for (Player p : player.getGame().getPlayers()) {
            if (p != player && p.isAlive() && (!p.getCards().isEmpty() || !p.getMessageCards().isEmpty())) {
                players.add(p);
            }
        }
        if (players.isEmpty()) return false;
        if (ThreadLocalRandom.current().nextInt(players.size() + 1) != 0)
            return false;
        Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_sou_ji_a_tos.newBuilder().setTargetPlayerId(player.getAlternativeLocation(p.location())).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
