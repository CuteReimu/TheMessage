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
 * 王田香技能【禁闭】：出牌阶段限一次，你可以指定一名角色，除非其交给你两张手牌，否则其本回合不能使用手牌，且所有角色技能无效。
 */
public class JinBi extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(JinBi.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.JIN_BI;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("现在不是出牌阶段空闲时点");
            return;
        }
        if (r.getSkillUseCount(getSkillId()) > 0) {
            log.error("[禁闭]一回合只能发动一次");
            return;
        }
        var pb = (Role.skill_jin_bi_a_tos) message;
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
        log.info(r + "对" + target + "发动了[禁闭]");
        g.resolve(new executeJinBi(r, target));
    }

    private record executeJinBi(Player r, Player target) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJinBi.class);

        @Override
        public ResolveResult resolve() {
            if (target.getCards().size() < 2) {
                doExecuteJinBi();
                return new ResolveResult(new MainPhaseIdle(r), true);
            }
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_jin_bi_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player.getAlternativeLocation(target.location()));
                    builder.setWaitingSecond(20);
                    if (player == target) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        GameExecutor.post(player.getGame(), () -> {
                            if (player.checkSeq(seq2))
                                player.getGame().tryContinueResolveProtocol(player, Role.skill_jin_bi_b_tos.newBuilder().setSeq(seq2).build());
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS);
                    }
                    player.send(builder.build());
                }
            }
            if (target instanceof RobotPlayer)
                GameExecutor.post(target.getGame(), () -> target.getGame().tryContinueResolveProtocol(target, Role.skill_jin_bi_b_tos.getDefaultInstance()), 2, TimeUnit.SECONDS);
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != target) {
                log.error("你不是被禁闭的目标");
                return null;
            }
            if (!(message instanceof Role.skill_jin_bi_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = target.getGame();
            if (target instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (pb.getCardIdsCount() == 0) {
                r.incrSeq();
                doExecuteJinBi();
                return new ResolveResult(new MainPhaseIdle(r), true);
            } else if (pb.getCardIdsCount() != 2) {
                log.error("给的牌数量不对：" + pb.getCardIdsCount());
                return null;
            }
            Card[] cards = new Card[2];
            for (int i = 0; i < cards.length; i++) {
                Card card = target.findCard(pb.getCardIds(i));
                if (card == null) {
                    log.error("没有这张牌");
                    return null;
                }
                cards[i] = card;
            }
            for (Card card : cards)
                target.deleteCard(card.getId());
            r.addCard(cards);
            log.info(target + "给了" + r + Arrays.toString(cards));
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_jin_bi_b_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    if (p == r || p == target) {
                        for (Card card : cards)
                            builder.addCards(card.toPbCard());
                    } else {
                        builder.setUnknownCardCount(2);
                    }
                    player1.send(builder.build());
                }
            }
            return new ResolveResult(new MainPhaseIdle(r), true);
        }

        private void doExecuteJinBi() {
            log.info(target + "进入了[禁闭]状态");
            Game g = r.getGame();
            g.setJinBiPlayer(target);
            Skill[] skills = target.getSkills();
            for (int i = 0; i < skills.length; i++)
                skills[i] = new JinBiSkill(skills[i]);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player)
                    player.send(Role.skill_jin_bi_b_toc.newBuilder()
                            .setPlayerId(player.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(player.getAlternativeLocation(target.location())).build());
            }
        }
    }

    private static class JinBiSkill extends AbstractSkill {
        public final Skill originSkill;

        private JinBiSkill(Skill originSkill) {
            this.originSkill = originSkill;
        }

        @Override
        public SkillId getSkillId() {
            return SkillId.BEI_JIN_BI;
        }
    }

    public static void resetJinBi(Game game) {
        Player player = game.getJinBiPlayer();
        if (player != null) {
            Skill[] skills = player.getSkills();
            for (int i = 0; i < skills.length; i++) {
                if (skills[i] instanceof JinBiSkill)
                    skills[i] = ((JinBiSkill) skills[i]).originSkill;
            }
            game.setJinBiPlayer(null);
        }
    }

    public static boolean ai(MainPhaseIdle e, final ActiveSkill skill) {
        if (e.player().getSkillUseCount(SkillId.JIN_BI) > 0)
            return false;
        List<Player> players = new ArrayList<>();
        for (Player p : e.player().getGame().getPlayers()) {
            if (p != e.player() && p.isAlive())
                players.add(p);
        }
        if (players.isEmpty())
            return false;
        final Player player = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        GameExecutor.post(e.player().getGame(), () -> skill.executeProtocol(
                e.player().getGame(), e.player(), Role.skill_jin_bi_a_tos.newBuilder().setTargetPlayerId(player.location()).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
