package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.card.PlayerAndCard;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Errcode;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 黄济仁技能【对症下药】：争夺阶段，你可以翻开此角色牌，然后摸三张牌，并且你可以展示两张含有相同颜色的手牌，然后从一名角色的情报区，弃置一张对应颜色情报。
 */
public class DuiZhengXiaYao extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(DuiZhengXiaYao.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.DUI_ZHENG_XIA_YAO;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("现在不是发动[对症下药]的时机");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[对症下药]");
            return;
        }
        var pb = (Role.skill_dui_zheng_xia_yao_a_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.playerSetRoleFaceUp(r, true);
        log.info(r + "发动了[对症下药]");
        r.draw(3);
        g.resolve(new executeDuiZhengXiaYaoA(fsm, r));
    }

    private record executeDuiZhengXiaYaoA(FightPhaseIdle fsm, Player r) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeDuiZhengXiaYaoA.class);

        @Override
        public ResolveResult resolve() {
            final Game g = r.getGame();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_dui_zheng_xia_yao_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(15);
                    if (player == r) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, Role.skill_dui_zheng_xia_yao_b_tos.newBuilder()
                                        .setEnable(false).setSeq(seq2).build());
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    EnumMap<Common.color, List<Card>> cache = new EnumMap<>(Common.color.class);
                    cache.put(Common.color.Black, new ArrayList<>());
                    cache.put(Common.color.Red, new ArrayList<>());
                    cache.put(Common.color.Blue, new ArrayList<>());
                    for (Card card : r.getCards().values()) {
                        for (Common.color color : card.getColors())
                            cache.get(color).add(card);
                    }
                    for (var colorAndCards : cache.entrySet()) {
                        List<Card> cards = colorAndCards.getValue();
                        if (cards.size() >= 2 && findColorMessageCard(g, List.of(colorAndCards.getKey())) != null) {
                            var builder = Role.skill_dui_zheng_xia_yao_b_tos.newBuilder().setEnable(true);
                            for (Card card : cards) {
                                builder.addCardIds(card.getId());
                                if (builder.getCardIdsCount() >= 2)
                                    break;
                            }
                            g.tryContinueResolveProtocol(r, builder.build());
                            return;
                        }
                    }
                    g.tryContinueResolveProtocol(r, Role.skill_dui_zheng_xia_yao_b_tos.newBuilder().setEnable(false).build());
                }, 1, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != r) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_dui_zheng_xia_yao_b_tos pb)) {
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
                for (Player p : g.getPlayers()) {
                    if (p instanceof HumanPlayer player1)
                        player1.send(Role.skill_dui_zheng_xia_yao_b_toc.newBuilder().setPlayerId(player1.getAlternativeLocation(r.location())).setEnable(false).build());
                }
                fsm.whoseFightTurn = fsm.inFrontOfWhom;
                return new ResolveResult(fsm, true);
            }
            if (pb.getCardIdsCount() != 2) {
                log.error("enable为true时必须要发两张牌");
                return null;
            }
            Card[] cards = new Card[2];
            for (int i = 0; i < cards.length; i++) {
                Card card = r.findCard(pb.getCardIds(i));
                if (card == null) {
                    log.error("没有这张卡");
                    return null;
                }
                cards[i] = card;
            }
            List<Common.color> colors = getSameColors(cards[0], cards[1]);
            if (colors.isEmpty()) {
                log.error("两张牌没有相同的颜色");
                return null;
            }
            PlayerAndCard playerAndCard = findColorMessageCard(g, colors);
            if (playerAndCard == null) {
                log.error("场上没有选择的颜色的情报牌");
                if (player instanceof HumanPlayer humanPlayer)
                    humanPlayer.send(Errcode.error_code_toc.newBuilder().setCode(Errcode.error_code.no_color_message_card).build());
                return null;
            }
            r.incrSeq();
            return new ResolveResult(new executeDuiZhengXiaYaoB(fsm, r, cards, colors, playerAndCard), true);
        }
    }

    private record executeDuiZhengXiaYaoB(FightPhaseIdle fsm, Player r, Card[] cards, List<Common.color> colors,
                                          PlayerAndCard defaultSelection) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeDuiZhengXiaYaoB.class);

        @Override
        public ResolveResult resolve() {
            log.info(r + "展示了" + Arrays.toString(cards));
            Game g = r.getGame();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_dui_zheng_xia_yao_b_toc.newBuilder().setEnable(true);
                    for (Card card : cards)
                        builder.addCards(card.toPbCard());
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(15);
                    if (player1 == r) {
                        final int seq2 = player1.getSeq();
                        builder.setSeq(seq2);
                        player1.setTimeout(GameExecutor.post(g, () -> {
                            if (player1.checkSeq(seq2))
                                g.tryContinueResolveProtocol(r, Role.skill_dui_zheng_xia_yao_c_tos.newBuilder()
                                        .setTargetPlayerId(r.getAlternativeLocation(defaultSelection.player().location()))
                                        .setMessageCardId(defaultSelection.card().getId()).setSeq(seq2).build());
                        }, player1.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player1.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> g.tryContinueResolveProtocol(r, Role.skill_dui_zheng_xia_yao_c_tos.newBuilder()
                        .setTargetPlayerId(r.getAlternativeLocation(defaultSelection.player().location()))
                        .setMessageCardId(defaultSelection.card().getId()).build()), 1, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != r) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_dui_zheng_xia_yao_c_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (pb.getTargetPlayerId() < 0 || pb.getTargetPlayerId() >= g.getPlayers().length) {
                log.error("目标错误");
                return null;
            }
            Player target = g.getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
            if (!target.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            Card card = target.findMessageCard(pb.getMessageCardId());
            if (card == null) {
                log.error("没有这张牌");
                return null;
            }
            boolean contains = false;
            for (Common.color color : colors) {
                if (card.getColors().contains(color)) {
                    contains = true;
                    break;
                }
            }
            if (!contains) {
                log.error("选择的情报不含有指定的颜色");
                return null;
            }
            r.incrSeq();
            log.info(r + "弃掉了" + target + "面前的" + card);
            g.getDeck().discard(target.deleteMessageCard(card.getId()));
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_dui_zheng_xia_yao_c_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    builder.setMessageCardId(card.getId());
                    player1.send(builder.build());
                }
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            return new ResolveResult(fsm, true);
        }
    }

    private static List<Common.color> getSameColors(Card card1, Card card2) {
        List<Common.color> colors = new ArrayList<>();
        for (Common.color color : new Common.color[]{Common.color.Black, Common.color.Red, Common.color.Blue}) {
            if (card1.getColors().contains(color) && card2.getColors().contains(color))
                colors.add(color);
        }
        return colors;
    }

    private static PlayerAndCard findColorMessageCard(Game game, List<Common.color> colors) {
        for (Player player : game.getPlayers()) {
            for (Card card : player.getMessageCards().values()) {
                for (Common.color color : card.getColors()) {
                    if (colors.contains(color))
                        return new PlayerAndCard(player, card);
                }
            }
        }
        return null;
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        Player player = e.whoseFightTurn;
        if (player.isRoleFaceUp())
            return false;
        int playerCount = player.getGame().getPlayers().length;
        List<PlayerAndCard> playerAndCards = new ArrayList<>();
        for (Player p : player.getGame().getPlayers()) {
            if (p.isAlive()) {
                for (Card c : p.getMessageCards().values())
                    playerAndCards.add(new PlayerAndCard(p, c));
            }
        }
        if (playerAndCards.size() < playerCount) return false;
        if (ThreadLocalRandom.current().nextInt(playerCount * playerCount) != 0)
            return false;
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_dui_zheng_xia_yao_a_tos.getDefaultInstance()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
