package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 小九技能【广发报】：争夺阶段，你可以翻开此角色牌，然后摸三张牌，并且你可以将你的任意张手牌置入任意名角色的情报区。你不能通过此技能让任何角色收集三张或更多的同色情报。
 */
public class GuangFaBao extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(GuangFaBao.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.GUANG_FA_BAO;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("现在不是发动[广发报]的时机");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[广发报]");
            return;
        }
        var pb = (Role.skill_guang_fa_bao_a_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.playerSetRoleFaceUp(r, true);
        log.info(r + "发动了[广发报]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_guang_fa_bao_a_toc.newBuilder().setPlayerId(player.getAlternativeLocation(r.location())).build());
        }
        r.draw(3);
        g.resolve(new executeGuangFaBao(fsm, r));
    }

    private record executeGuangFaBao(FightPhaseIdle fsm, Player r) implements WaitingFsm {
        @Override
        public ResolveResult resolve() {
            final Game g = r.getGame();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_wait_for_guang_fa_bao_b_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(15);
                    if (player == r) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, Role.skill_guang_fa_bao_b_tos.newBuilder()
                                        .setEnable(false).setSeq(seq2).build());
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    for (Player p : g.getPlayers()) {
                        List<Integer> cardIds = new ArrayList<>();
                        List<Card> cards = new ArrayList<>();
                        for (Card card : r.getCards().values()) {
                            cards.add(card);
                            if (p.checkThreeSameMessageCard(cards.toArray(new Card[0])))
                                cards.remove(card);
                            else
                                cardIds.add(card.getId());
                        }
                        if (!cardIds.isEmpty()) {
                            g.tryContinueResolveProtocol(r, Role.skill_guang_fa_bao_b_tos.newBuilder().setEnable(true)
                                    .setTargetPlayerId(r.getAlternativeLocation(p.location())).addAllCardIds(cardIds).build());
                            return;
                        }
                    }
                    g.tryContinueResolveProtocol(r, Role.skill_guang_fa_bao_b_tos.newBuilder().setEnable(false).build());
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
            if (!(message instanceof Role.skill_guang_fa_bao_b_tos pb)) {
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
                        player1.send(Role.skill_guang_fa_bao_b_toc.newBuilder().setPlayerId(player1.getAlternativeLocation(r.location())).setEnable(false).build());
                }
                fsm.whoseFightTurn = fsm.inFrontOfWhom;
                return new ResolveResult(fsm, true);
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
            if (pb.getCardIdsCount() == 0) {
                log.error("enable为true时至少要发一张牌");
                return null;
            }
            Card[] cards = new Card[pb.getCardIdsCount()];
            for (int i = 0; i < cards.length; i++) {
                Card card = r.findCard(pb.getCardIds(i));
                if (card == null) {
                    log.error("没有这张卡");
                    return null;
                }
                cards[i] = card;
            }
            if (target.checkThreeSameMessageCard(cards)) {
                log.error("你不能通过此技能让任何角色收集三张或更多的同色情报");
                return null;
            }
            r.incrSeq();
            log.info(r + "将" + Arrays.toString(cards) + "置于" + target + "的情报区");
            for (Card card : cards)
                r.deleteCard(card.getId());
            target.addMessageCard(cards);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_guang_fa_bao_b_toc.newBuilder().setEnable(true);
                    for (Card card : cards)
                        builder.addCards(card.toPbCard());
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    player1.send(builder.build());
                }
            }
            if (!r.getCards().isEmpty())
                return new ResolveResult(this, true);
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        Player player = e.whoseFightTurn;
        if (player.isRoleFaceUp())
            return false;
        if (player.getCards().size() < 5)
            return false;
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_guang_fa_bao_a_tos.newBuilder().build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
