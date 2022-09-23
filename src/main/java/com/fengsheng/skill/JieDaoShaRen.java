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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 商玉技能【借刀杀人】：争夺阶段，你可以翻开此角色牌，然后抽取另一名角色的一张手牌并展示之。若展示的牌是：<b>黑色</b>，则你可以将其置入一名角色的情报区，并将你的角色牌翻至面朝下。<b>非黑色</b>，则你摸一张牌。
 */
public class JieDaoShaRen extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(JieDaoShaRen.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.JIE_DAO_SHA_REN;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("现在不是发动[借刀杀人]的时机");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[借刀杀人]");
            return;
        }
        var pb = (Role.skill_jie_dao_sha_ren_a_tos) message;
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
        g.playerSetRoleFaceUp(r, true);
        Card[] cards = target.getCards().values().toArray(new Card[0]);
        Card card = cards[ThreadLocalRandom.current().nextInt(cards.length)];
        g.resolve(new excuteJieDaoShaRen(fsm, target, card));
    }

    private record excuteJieDaoShaRen(FightPhaseIdle fsm, Player target, Card card) implements WaitingFsm {
        @Override
        public ResolveResult resolve() {
            final Player r = fsm.whoseFightTurn;
            final Game g = r.getGame();
            target.deleteCard(card.getId());
            r.addCard(card);
            log.info(r + "对" + target + "发动了[借刀杀人]，抽取了一张手牌" + card);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Role.skill_jie_dao_sha_ren_a_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player.getAlternativeLocation(target.location()));
                    builder.setCard(card.toPbCard());
                    if (card.getColors().contains(Common.color.Black)) {
                        builder.setWaitingSecond(20);
                        if (player == r) {
                            final int seq2 = player.getSeq();
                            builder.setSeq(seq2);
                            player.setTimeout(GameExecutor.post(g, () -> {
                                if (player.checkSeq(seq2)) {
                                    g.tryContinueResolveProtocol(r, Role.skill_jie_dao_sha_ren_b_tos.newBuilder()
                                            .setEnable(false).setSeq(seq2).build());
                                }
                            }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                        }
                    }
                    player.send(builder.build());
                }
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom;
            if (!card.getColors().contains(Common.color.Black)) {
                r.draw(1);
                return new ResolveResult(fsm, true);
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    List<Player> players = new ArrayList<>();
                    for (Player p : g.getPlayers())
                        if (p != r && p.isAlive()) players.add(p);
                    if (players.isEmpty()) {
                        g.tryContinueResolveProtocol(r, Role.skill_jie_dao_sha_ren_b_tos.newBuilder().setEnable(false).build());
                    } else {
                        Player target2 = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                        g.tryContinueResolveProtocol(r, Role.skill_jie_dao_sha_ren_b_tos.newBuilder().setEnable(true)
                                .setTargetPlayerId(r.getAlternativeLocation(target2.location())).build());
                    }
                }, 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.whoseFightTurn) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_jie_dao_sha_ren_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.whoseFightTurn;
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
            Player target = g.getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
            if (!target.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            r.incrSeq();
            log.info(r + "将" + card + "置于" + target + "的情报区");
            r.deleteCard(card.getId());
            target.addMessageCard(card);
            var newFsm = new CheckWin(fsm.whoseTurn, fsm);
            newFsm.receiveOrder.addPlayerIfHasThreeBlack(target);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_jie_dao_sha_ren_b_toc.newBuilder().setCard(card.toPbCard())
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(player1.getAlternativeLocation(target.location())).build());
            }
            g.playerSetRoleFaceUp(r, false);
            return new ResolveResult(newFsm, true);
        }
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        Player player = e.whoseFightTurn;
        if (player.isRoleFaceUp())
            return false;
        List<Player> players = new ArrayList<>();
        for (Player p : player.getGame().getPlayers()) {
            if (p != player && p.isAlive() && !p.getCards().isEmpty()) {
                boolean notBlack = false;
                for (Card card : p.getCards().values()) {
                    if (!card.getColors().contains(Common.color.Black)) {
                        notBlack = true;
                        break;
                    }
                }
                if (!notBlack) players.add(p);
            }
        }
        if (players.isEmpty()) return false;
        Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_jie_dao_sha_ren_a_tos.newBuilder()
                        .setTargetPlayerId(player.getAlternativeLocation(target.location())).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
