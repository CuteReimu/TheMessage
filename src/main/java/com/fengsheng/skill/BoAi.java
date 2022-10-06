package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 白沧浪技能【博爱】：出牌阶段限一次，你可以摸一张牌，然后可以将一张手牌交给另一名角色，若交给了女性角色，则你再摸一张牌。
 */
public class BoAi extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(BoAi.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.BO_AI;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("现在不是出牌阶段空闲时点");
            return;
        }
        if (r.getSkillUseCount(getSkillId()) > 0) {
            log.error("[博爱]一回合只能发动一次");
            return;
        }
        var pb = (Role.skill_bo_ai_a_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.resolve(new executeBoAi(r));
    }

    private record executeBoAi(Player r) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeBoAi.class);

        @Override
        public ResolveResult resolve() {
            Game g = r.getGame();
            log.info(r + "发动了[博爱]");
            r.draw(1);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_bo_ai_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setWaitingSecond(20);
                    if (player == r) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(g, () -> {
                            if (player.checkSeq(seq2)) {
                                r.getGame().tryContinueResolveProtocol(r, Role.skill_bo_ai_b_tos.newBuilder().setCardId(0).setSeq(seq2).build());
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    List<Player> players = new ArrayList<>();
                    for (Player player : r.getGame().getPlayers()) {
                        if (player.isAlive() && player != r && player.isFemale())
                            players.add(player);
                    }
                    if (players.isEmpty()) {
                        r.getGame().tryContinueResolveProtocol(r, Role.skill_bo_ai_b_tos.newBuilder().setCardId(0).build());
                        return;
                    }
                    final Player player = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                    for (Card card : r.getCards().values()) {
                        r.getGame().tryContinueResolveProtocol(r, Role.skill_bo_ai_b_tos.newBuilder().setCardId(card.getId())
                                .setTargetPlayerId(r.getAlternativeLocation(player.location())).build());
                        break;
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
            if (!(message instanceof Role.skill_bo_ai_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (pb.getCardId() == 0) {
                r.incrSeq();
                return new ResolveResult(new MainPhaseIdle(r), true);
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
            Card card = r.findCard(pb.getCardId());
            if (card == null) {
                log.error("没有这张卡");
                return null;
            }
            r.incrSeq();
            log.info(r + "将" + card + "交给" + target);
            r.deleteCard(card.getId());
            target.addCard(card);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_bo_ai_b_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    if (player1 == r || player1 == target)
                        builder.setCard(card.toPbCard());
                    player1.send(builder.build());
                }
            }
            if (target.isFemale())
                r.draw(1);
            return new ResolveResult(new MainPhaseIdle(r), true);
        }
    }

    public static boolean ai(MainPhaseIdle e, final ActiveSkill skill) {
        if (e.player().getSkillUseCount(SkillId.BO_AI) > 0)
            return false;
        GameExecutor.post(e.player().getGame(), () -> skill.executeProtocol(
                e.player().getGame(), e.player(), Role.skill_bo_ai_a_tos.getDefaultInstance()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
