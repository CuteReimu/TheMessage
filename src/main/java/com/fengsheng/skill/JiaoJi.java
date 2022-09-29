package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 裴玲技能【交际】：出牌阶段限一次，你可以抽取一名角色的最多两张手牌。然后将等量手牌交给该角色。你每收集一张黑色情报，便可以少交一张牌。
 */
public class JiaoJi extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(JiaoJi.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.JIAO_JI;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("现在不是出牌阶段空闲时点");
            return;
        }
        if (r.getSkillUseCount(getSkillId()) > 0) {
            log.error("[交际]一回合只能发动一次");
            return;
        }
        var pb = (Role.skill_jiao_ji_a_tos) message;
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
        if (target.getCards().isEmpty()) {
            log.error("目标没有手牌");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        Random random = ThreadLocalRandom.current();
        List<Card> cardList = new ArrayList<>();
        for (int i = 0; i < 2 && !target.getCards().isEmpty(); i++) {
            Card[] handCards = target.getCards().values().toArray(new Card[0]);
            cardList.add(target.deleteCard(handCards[random.nextInt(handCards.length)].getId()));
        }
        Card[] cards = cardList.toArray(new Card[0]);
        log.info(r + "对" + target + "发动了[交际]，抽取了" + Arrays.toString(cards));
        for (Card card : cards) {
            target.deleteCard(card.getId());
            r.addCard(card);
        }
        int black = 0;
        for (Card card : r.getMessageCards().values()) {
            if (card.getColors().contains(Common.color.Black))
                black++;
        }
        final int needReturnCount = Math.max(0, cards.length - black);
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player) {
                var builder = Role.skill_jiao_ji_a_toc.newBuilder();
                builder.setPlayerId(player.getAlternativeLocation(r.location()));
                builder.setTargetPlayerId(player.getAlternativeLocation(target.location()));
                if (player == r || player == target) {
                    for (Card card : cards)
                        builder.addCards(card.toPbCard());
                } else {
                    builder.setUnknownCardCount(cards.length);
                }
                if (needReturnCount > 0) {
                    builder.setWaitingSecond(20);
                    if (player == r) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2)) {
                                var builder2 = Role.skill_jiao_ji_b_tos.newBuilder().setSeq(seq2);
                                int i = 0;
                                for (Card c : r.getCards().values()) {
                                    if (i >= needReturnCount)
                                        break;
                                    builder2.addCardIds(c.getId());
                                    i++;
                                }
                                g.tryContinueResolveProtocol(r, builder2.build());
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                }
                player.send(builder.build());
            }
        }
        if (needReturnCount == 0) {
            g.continueResolve();
            return;
        }
        if (r instanceof RobotPlayer) {
            GameExecutor.post(g, () -> {
                var builder2 = Role.skill_jiao_ji_b_tos.newBuilder();
                int i = 0;
                for (Card c : r.getCards().values()) {
                    if (i >= needReturnCount)
                        break;
                    builder2.addCardIds(c.getId());
                    i++;
                }
                g.tryContinueResolveProtocol(r, builder2.build());
            }, 2, TimeUnit.SECONDS);
        }
        g.resolve(new excuteJiaoJi(fsm, target, needReturnCount));
    }

    private record excuteJiaoJi(MainPhaseIdle fsm, Player target, int needReturnCount) implements WaitingFsm {
        @Override
        public ResolveResult resolve() {
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.player()) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_jiao_ji_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            if (pb.getCardIdsCount() != needReturnCount) {
                log.error("卡牌数量不正确，需要返还：" + needReturnCount + "，实际返还：" + pb.getCardIdsCount());
                return null;
            }
            Player r = fsm.player();
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            Card[] cards = new Card[needReturnCount];
            for (int i = 0; i < needReturnCount; i++) {
                Card card = r.findCard(pb.getCardIds(i));
                if (card == null) {
                    log.error("没有这张卡");
                    return null;
                }
                cards[i] = card;
            }
            r.incrSeq();
            log.info(r + "将" + Arrays.toString(cards) + "还给" + target);
            for (Card card : cards) {
                r.deleteCard(card.getId());
                target.addCard(card);
            }
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_jiao_ji_b_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    if (player1 == r || player1 == target) {
                        for (Card card : cards)
                            builder.addCards(card.toPbCard());
                    } else {
                        builder.setUnknownCardCount(cards.length);
                    }
                    player1.send(builder.build());
                }
            }
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(MainPhaseIdle e, final ActiveSkill skill) {
        final Player player = e.player();
        if (player.getSkillUseCount(SkillId.JIAO_JI) > 0)
            return false;
        List<Player> players = new ArrayList<>();
        for (Player p : player.getGame().getPlayers()) {
            if (p != player && p.isAlive() && !p.getCards().isEmpty()) players.add(p);
        }
        if (players.isEmpty())
            return false;
        Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_jiao_ji_a_tos.newBuilder()
                        .setTargetPlayerId(player.getAlternativeLocation(target.location())).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
