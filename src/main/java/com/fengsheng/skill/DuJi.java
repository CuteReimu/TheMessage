package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.CheckWin;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 白昆山技能【毒计】：争夺阶段，你可以翻开此角色牌，然后指定两名其他角色，令他们相互抽取对方的一张手牌并展示之，你将展示的牌加入你的手牌，若展示的是黑色牌，你可以改为令抽取者选择一项：
 * <ul><li>将其置入自己的情报区</li><li>将其置入对方的情报区</li></ul>
 */
public class DuJi extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(DuJi.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.DU_JI;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("现在不是发动[毒计]的时机");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[毒计]");
            return;
        }
        var pb = (Role.skill_du_ji_a_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (pb.getTargetPlayerIdsCount() != 2) {
            log.error("[毒计]必须选择两名角色为目标");
            return;
        }
        int idx1 = pb.getTargetPlayerIds(0);
        int idx2 = pb.getTargetPlayerIds(1);
        if (idx1 < 0 || idx1 >= g.getPlayers().length || idx2 < 0 || idx2 >= g.getPlayers().length) {
            log.error("目标错误");
            return;
        }
        if (idx1 == 0 || idx2 == 0) {
            log.error("不能以自己为目标");
            return;
        }
        Player target1 = g.getPlayers()[r.getAbstractLocation(idx1)];
        Player target2 = g.getPlayers()[r.getAbstractLocation(idx2)];
        if (!target1.isAlive() || !target2.isAlive()) {
            log.error("目标已死亡");
            return;
        }
        if (target1.getCards().isEmpty() || target2.getCards().isEmpty()) {
            log.error("目标没有手牌");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.playerSetRoleFaceUp(r, true);
        Card[] cards1 = target1.getCards().values().toArray(new Card[0]);
        Card[] cards2 = target2.getCards().values().toArray(new Card[0]);
        Card card1 = cards1[ThreadLocalRandom.current().nextInt(cards1.length)];
        Card card2 = cards2[ThreadLocalRandom.current().nextInt(cards2.length)];
        log.info(r + "发动了[毒计]，抽取了" + target1 + "的" + card1 + "和" + target2 + "的" + card2);
        target1.deleteCard(card1.getId());
        target2.deleteCard(card2.getId());
        r.addCard(card1, card2);
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player) {
                var builder = Role.skill_du_ji_a_toc.newBuilder();
                builder.setPlayerId(player.getAlternativeLocation(r.location()));
                builder.addTargetPlayerIds(player.getAlternativeLocation(target1.location()));
                builder.addTargetPlayerIds(player.getAlternativeLocation(target2.location()));
                builder.addCards(card1.toPbCard());
                builder.addCards(card2.toPbCard());
                player.send(builder.build());
            }
        }
        fsm.whoseFightTurn = fsm.inFrontOfWhom;
        List<TwoPlayersAndCard> twoPlayersAndCards = new ArrayList<>();
        if (card1.getColors().contains(Common.color.Black))
            twoPlayersAndCards.add(new TwoPlayersAndCard(target1, target2, card1));
        if (card2.getColors().contains(Common.color.Black))
            twoPlayersAndCards.add(new TwoPlayersAndCard(target2, target1, card2));
        g.resolve(new executeDuJiA(new CheckWin(fsm.whoseTurn, fsm), r, twoPlayersAndCards));
    }

    private record executeDuJiA(CheckWin fsm, Player r,
                                List<TwoPlayersAndCard> playerAndCards) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeDuJiA.class);

        @Override
        public ResolveResult resolve() {
            if (playerAndCards.isEmpty())
                return new ResolveResult(fsm, true);
            final Game g = r.getGame();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_wait_for_du_ji_b_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    for (TwoPlayersAndCard twoPlayersAndCard : playerAndCards) {
                        builder.addTargetPlayerIds(player.getAlternativeLocation(twoPlayersAndCard.waitingPlayer().location()));
                        builder.addCardIds(twoPlayersAndCard.card().getId());
                    }
                    builder.setWaitingSecond(15);
                    if (player == r) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, Role.skill_du_ji_b_tos.newBuilder()
                                        .setEnable(false).setSeq(seq2).build());
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    if (!playerAndCards.isEmpty())
                        g.tryContinueResolveProtocol(r, Role.skill_du_ji_b_tos.newBuilder().setEnable(true)
                                .setCardId(playerAndCards.get(0).card().getId()).build());
                    else
                        g.tryContinueResolveProtocol(r, Role.skill_du_ji_b_tos.newBuilder().setEnable(false).build());
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
            if (!(message instanceof Role.skill_du_ji_b_tos pb)) {
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
                        player1.send(Role.skill_du_ji_b_toc.newBuilder().setPlayerId(player1.getAlternativeLocation(r.location())).setEnable(false).build());
                }
                return new ResolveResult(fsm, true);
            }
            TwoPlayersAndCard selection = null;
            for (TwoPlayersAndCard twoPlayersAndCard : playerAndCards) {
                if (twoPlayersAndCard.card().getId() == pb.getCardId()) {
                    selection = twoPlayersAndCard;
                    break;
                }
            }
            if (selection == null) {
                log.error("目标卡牌不存在");
                return null;
            }
            r.incrSeq();
            return new ResolveResult(new executeDuJiB(this, selection), true);
        }
    }

    private record executeDuJiB(executeDuJiA fsm, TwoPlayersAndCard selection) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeDuJiB.class);

        @Override
        public ResolveResult resolve() {
            log.info("等待" + selection.waitingPlayer() + "对" + selection.card() + "进行选择");
            Game g = selection.waitingPlayer().getGame();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_du_ji_b_toc.newBuilder().setEnable(true);
                    builder.setPlayerId(p.getAlternativeLocation(fsm.r().location()));
                    builder.setWaitingPlayerId(p.getAlternativeLocation(selection.waitingPlayer().location()));
                    builder.setTargetPlayerId(p.getAlternativeLocation(selection.fromPlayer().location()));
                    builder.setCard(selection.card().toPbCard());
                    builder.setWaitingSecond(15);
                    if (player1 == selection.waitingPlayer()) {
                        final int seq2 = player1.getSeq();
                        builder.setSeq(seq2);
                        player1.setTimeout(GameExecutor.post(g, () -> {
                            if (player1.checkSeq(seq2))
                                g.tryContinueResolveProtocol(selection.waitingPlayer(), Role.skill_du_ji_c_tos.newBuilder()
                                        .setInFrontOfMe(false).setSeq(seq2).build());
                        }, player1.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player1.send(builder.build());
                }
            }
            if (selection.waitingPlayer() instanceof RobotPlayer) {
                GameExecutor.post(g, () -> g.tryContinueResolveProtocol(selection.waitingPlayer(), Role.skill_du_ji_c_tos.newBuilder()
                        .setInFrontOfMe(false).build()), 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            Player r = selection.waitingPlayer();
            if (player != r) {
                log.error("当前没有轮到你结算[毒计]");
                return null;
            }
            if (!(message instanceof Role.skill_du_ji_c_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            r.incrSeq();
            Player target = pb.getInFrontOfMe() ? selection.waitingPlayer() : selection.fromPlayer();
            Card card = selection.card();
            log.info(r + "选择将" + card + "放在" + target + "面前");
            fsm.r().deleteCard(card.getId());
            target.addMessageCard(card);
            fsm.fsm().receiveOrder.addPlayerIfHasThreeBlack(target);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_du_ji_c_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(fsm.r().location()));
                    builder.setWaitingPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    builder.setCard(card.toPbCard());
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
            if (p != player && p.isAlive())
                players.add(p);
        }
        int playerCount = players.size();
        if (playerCount < 2) return false;
        if (ThreadLocalRandom.current().nextInt(playerCount * playerCount) != 0)
            return false;
        Random random = ThreadLocalRandom.current();
        int i = random.nextInt(playerCount);
        int j = random.nextInt(playerCount);
        j = i == j ? (j + 1) % playerCount : j;
        final Player player1 = players.get(i);
        final Player player2 = players.get(j);
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_du_ji_a_tos.newBuilder()
                        .addTargetPlayerIds(player.getAlternativeLocation(player1.location()))
                        .addTargetPlayerIds(player.getAlternativeLocation(player2.location())).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }

    private record TwoPlayersAndCard(Player fromPlayer, Player waitingPlayer, Card card) {

    }
}
