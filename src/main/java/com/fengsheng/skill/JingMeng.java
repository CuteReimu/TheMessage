package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.ReceivePhaseReceiverSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * 程小蝶技能【惊梦】A：你接收黑色情报后，可以查看一名角色的手牌。
 */
public class JingMeng extends AbstractSkill implements TriggeredSkill {
    @Override
    public void init(Game g) {
        g.addListeningSkill(this);
    }

    @Override
    public SkillId getSkillId() {
        return SkillId.JING_MENG;
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
        return new ResolveResult(new executeJingMengA(fsm), true);
    }

    private record executeJingMengA(ReceivePhaseReceiverSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJingMengA.class);

        @Override
        public ResolveResult resolve() {
            for (Player p : fsm.whoseTurn().getGame().getPlayers())
                p.notifyReceivePhase(fsm.whoseTurn(), fsm.inFrontOfWhom(), fsm.messageCard(), fsm.inFrontOfWhom(), 20);
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
            if (!(message instanceof Role.skill_jing_meng_a_tos pb)) {
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
                log.error("目标错误：" + pb.getTargetPlayerId());
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
            if (target.getCards().isEmpty()) {
                log.error("目标没有手牌");
                return null;
            }
            r.incrSeq();
            log.info(r + "发动了[惊梦]，查看了" + target + "的手牌");
            return new ResolveResult(new executeJingMengB(fsm, target), true);
        }
    }

    private record executeJingMengB(ReceivePhaseReceiverSkill fsm, Player target) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeJingMengB.class);

        @Override
        public ResolveResult resolve() {
            Player r = fsm.inFrontOfWhom();
            Game g = r.getGame();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_jing_meng_a_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    builder.setWaitingSecond(20);
                    if (player1 == r) {
                        for (Card card : target.getCards().values())
                            builder.addCards(card.toPbCard());
                        final int seq2 = player1.getSeq();
                        builder.setSeq(seq2);
                        player1.setTimeout(GameExecutor.post(g, () -> {
                            if (player1.checkSeq(seq2)) {
                                Card card = null;
                                for (Card c : target.getCards().values()) {
                                    card = c;
                                    break;
                                }
                                assert card != null;
                                player1.getGame().tryContinueResolveProtocol(player1, Role.skill_jing_meng_b_tos.newBuilder()
                                        .setCardId(card.getId()).setSeq(seq2).build());
                            }
                        }, player1.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player1.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                GameExecutor.post(g, () -> {
                    Card card = null;
                    for (Card c : target.getCards().values()) {
                        card = c;
                        break;
                    }
                    assert card != null;
                    r.getGame().tryContinueResolveProtocol(r, Role.skill_jing_meng_b_tos.newBuilder().setCardId(card.getId()).build());
                }, 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.inFrontOfWhom()) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_jing_meng_b_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.inFrontOfWhom();
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            Card card = target.findCard(pb.getCardId());
            if (card == null) {
                log.error("没有这张牌");
                return null;
            }
            r.incrSeq();
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1) {
                    var builder = Role.skill_jing_meng_b_toc.newBuilder();
                    builder.setPlayerId(player1.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player1.getAlternativeLocation(target.location()));
                    builder.setCard(card.toPbCard());
                    player1.send(builder.build());
                }
            }
            g.playerDiscardCard(target, card);
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(Fsm fsm0) {
        if (!(fsm0 instanceof executeJingMengA fsm))
            return false;
        Player p = fsm.fsm().inFrontOfWhom();
        Player target = fsm.fsm().whoseTurn();
        if (p == target || !target.isAlive() || target.getCards().isEmpty())
            return false;
        GameExecutor.post(p.getGame(), () -> p.getGame().tryContinueResolveProtocol(p, Role.skill_jing_meng_a_tos.newBuilder()
                .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()), 2, TimeUnit.SECONDS);
        return true;
    }
}
