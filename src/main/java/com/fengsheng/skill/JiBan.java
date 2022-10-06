package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * SP顾小梦技能【羁绊】：出牌阶段限一次，可以摸两张牌，然后将至少一张手牌交给另一名角色。
 */
public class JiBan extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(JiBan.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.JI_BAN;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("现在不是出牌阶段空闲时点");
            return;
        }
        if (r.getSkillUseCount(getSkillId()) > 0) {
            log.error("[羁绊]一回合只能发动一次");
            return;
        }
        var pb = (Role.skill_ji_ban_a_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.resolve(new executeJiBan(r));
    }

    private record executeJiBan(Player r) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJiBan.class);

        @Override
        public ResolveResult resolve() {
            Game g = r.getGame();
            log.info(r + "发动了[羁绊]");
            r.draw(2);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_ji_ban_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(20);
                    if (player == r) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2))
                                autoSelect(seq2);
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer)
                GameExecutor.post(g, () -> autoSelect(0), 2, TimeUnit.SECONDS);
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != r) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_ji_ban_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (pb.getCardIdsCount() == 0) {
                log.error("至少需要选择一张卡牌");
                return null;
            }
            if (pb.getTargetPlayerId() < 0 || pb.getTargetPlayerId() >= g.getPlayers().length) {
                log.error("目标错误");
                return null;
            }
            if (pb.getTargetPlayerId() == 0) {
                log.error("不能以自己为目标");
                return null;
            }
            Player target = g.getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
            if (!target.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            Card[] cards = new Card[pb.getCardIdsCount()];
            for (int i = 0; i < pb.getCardIdsCount(); i++) {
                Card card = r.findCard(pb.getCardIds(i));
                if (card == null) {
                    log.error("没有这张卡");
                    return null;
                }
                cards[i] = card;
            }
            r.incrSeq();
            log.info(r + "将" + Arrays.toString(cards) + "交给" + target);
            for (Card card : cards)
                r.deleteCard(card.getId());
            target.addCard(cards);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_ji_ban_b_toc.newBuilder();
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
            return new ResolveResult(new MainPhaseIdle(r), true);
        }

        private void autoSelect(int seq) {
            List<Player> players = new ArrayList<>();
            for (Player player : r.getGame().getPlayers()) {
                if (player.isAlive() && player != r)
                    players.add(player);
            }
            final Player player = players.get(ThreadLocalRandom.current().nextInt(players.size()));
            for (Card card : r.getCards().values()) {
                r.getGame().tryContinueResolveProtocol(r, Role.skill_ji_ban_b_tos.newBuilder().addCardIds(card.getId()).setSeq(seq)
                        .setTargetPlayerId(r.getAlternativeLocation(player.location())).build());
                break;
            }
        }
    }

    public static boolean ai(MainPhaseIdle e, final ActiveSkill skill) {
        if (e.player().getSkillUseCount(SkillId.JI_BAN) > 0)
            return false;
        GameExecutor.post(e.player().getGame(), () -> skill.executeProtocol(
                e.player().getGame(), e.player(), Role.skill_ji_ban_a_tos.getDefaultInstance()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
