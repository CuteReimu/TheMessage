package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.card.PlayerAndCard;
import com.fengsheng.phase.ReceivePhaseReceiverSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 吴志国技能【坚韧】：你接收黑色情报后，可以展示牌堆顶的一张牌，若是黑色牌，则将展示的牌加入你的手牌，并从一名角色的情报区弃置一张黑色情报。
 */
public class JianRen extends AbstractSkill implements TriggeredSkill {
    @Override
    public SkillId getSkillId() {
        return SkillId.JIAN_REN;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof ReceivePhaseReceiverSkill fsm) || fsm.inFrontOfWhom().findSkill(getSkillId()) == null)
            return null;
        if (fsm.inFrontOfWhom().getSkillUseCount(getSkillId()) > 0)
            return null;
        if (!fsm.messageCard().getColors().contains(Common.color.Black))
            return null;
        fsm.inFrontOfWhom().addSkillUseCount(getSkillId());
        return new ResolveResult(new executeJianRenA(fsm), true);
    }

    private record executeJianRenA(ReceivePhaseReceiverSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJianRenA.class);

        @Override
        public ResolveResult resolve() {
            for (Player p : fsm.whoseTurn().getGame().getPlayers())
                p.notifyReceivePhase(fsm.whoseTurn(), fsm.inFrontOfWhom(), fsm.messageCard(), fsm.inFrontOfWhom(), 15);
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.inFrontOfWhom()) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (message instanceof Fengsheng.end_receive_phase_tos pb) {
                if (player instanceof HumanPlayer r && !r.checkSeq(pb.getSeq())) {
                    log.error("操作太晚了, required Seq: " + r.getSeq() + ", actual Seq: " + pb.getSeq());
                    return null;
                }
                player.incrSeq();
                return new ResolveResult(fsm, true);
            }
            if (!(message instanceof Role.skill_jian_ren_a_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.inFrontOfWhom();
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            List<Card> cards = g.getDeck().peek(1);
            if (cards.isEmpty()) {
                log.error("牌堆没有牌了");
                return null;
            }
            r.incrSeq();
            log.info(r + "发动了[坚韧]，展示了" + cards.get(0));
            return new ResolveResult(new executeJianRenB(fsm, cards), true);
        }
    }


    private record executeJianRenB(ReceivePhaseReceiverSkill fsm, List<Card> cards) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJianRenB.class);

        @Override
        public ResolveResult resolve() {
            Player r = fsm.inFrontOfWhom();
            PlayerAndCard autoChoose = chooseBlackMessageCard(r);
            Card card = cards.get(0);
            boolean isBlack = card.getColors().contains(Common.color.Black);
            if (isBlack) {
                cards.clear();
                r.addCard(card);
                log.info(r + "将" + card + "加入手牌");
            }
            Game g = r.getGame();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_jian_ren_a_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setCard(card.toPbCard());
                    if (isBlack && autoChoose != null) {
                        builder.setWaitingSecond(15);
                        if (player1 == r) {
                            final int seq2 = player1.getSeq();
                            builder.setSeq(seq2);
                            player1.setTimeout(GameExecutor.post(g, () -> {
                                if (player1.checkSeq(seq2)) {
                                    player1.getGame().tryContinueResolveProtocol(player1, Role.skill_jian_ren_b_tos.newBuilder()
                                            .setTargetPlayerId(player1.getAlternativeLocation(autoChoose.player().location()))
                                            .setCardId(autoChoose.card().getId()).setSeq(seq2).build());
                                }
                            }, player1.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                        }
                    }
                    player1.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer && isBlack && autoChoose != null) {
                GameExecutor.post(g, () -> r.getGame().tryContinueResolveProtocol(r,
                        Role.skill_jian_ren_b_tos.newBuilder().setTargetPlayerId(r.getAlternativeLocation(autoChoose.player().location())).setCardId(autoChoose.card().getId()).build()), 2, TimeUnit.SECONDS);
            }
            return isBlack ? null : new ResolveResult(fsm, true);
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.inFrontOfWhom()) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_jian_ren_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.inFrontOfWhom();
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
            Card messageCard = target.findMessageCard(pb.getCardId());
            if (messageCard == null) {
                log.error("没有这张情报");
                return null;
            }
            if (!messageCard.getColors().contains(Common.color.Black)) {
                log.error("目标情报不是黑色的");
                return null;
            }
            r.incrSeq();
            log.info(r + "弃掉了" + target + "面前的" + messageCard);
            target.deleteMessageCard(messageCard.getId());
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_jian_ren_b_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    builder.setCardId(messageCard.getId());
                    player1.send(builder.build());
                }
            }
            g.playerDiscardCard(target, messageCard);
            return new ResolveResult(fsm, true);
        }
    }

    private static PlayerAndCard chooseBlackMessageCard(Player r) {
        for (Card card : r.getMessageCards().values()) {
            if (card.getColors().contains(Common.color.Black))
                return new PlayerAndCard(r, card);
        }
        for (Player p : r.getGame().getPlayers()) {
            if (p != r && p.isAlive()) {
                for (Card card : p.getMessageCards().values()) {
                    if (card.getColors().contains(Common.color.Black))
                        return new PlayerAndCard(p, card);
                }
            }
        }
        return null;
    }

    public static boolean ai(Fsm fsm0) {
        if (!(fsm0 instanceof executeJianRenA fsm))
            return false;
        Player p = fsm.fsm().inFrontOfWhom();
        GameExecutor.post(p.getGame(), () -> p.getGame().tryContinueResolveProtocol(p, Role.skill_jian_ren_a_tos.newBuilder().build()), 2, TimeUnit.SECONDS);
        return true;
    }
}
