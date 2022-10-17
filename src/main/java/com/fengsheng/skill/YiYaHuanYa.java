package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.ReceivePhaseReceiverSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 王魁技能【以牙还牙】：你接收黑色情报后，可以将一张黑色手牌置入情报传出者或其相邻角色的情报区，然后摸一张牌。
 */
public class YiYaHuanYa extends AbstractSkill implements TriggeredSkill {
    @Override
    public SkillId getSkillId() {
        return SkillId.YI_YA_HUAN_YA;
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
        return new ResolveResult(new executeYiYaHuanYa(fsm), true);
    }

    private record executeYiYaHuanYa(ReceivePhaseReceiverSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeYiYaHuanYa.class);

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
            if (!(message instanceof Role.skill_yi_ya_huan_ya_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.inFrontOfWhom();
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            Card card = r.findCard(pb.getCardId());
            if (card == null) {
                log.error("没有这张卡");
                return null;
            }
            if (!card.getColors().contains(Common.color.Black)) {
                log.error("你只能选择黑色手牌");
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
            if (target != fsm.whoseTurn() && target != fsm.whoseTurn().getNextLeftAlivePlayer() && target != fsm.whoseTurn().getNextRightAlivePlayer()) {
                log.error("你只能选择情报传出者或者其左边或右边的角色作为目标：" + pb.getTargetPlayerId());
                return null;
            }
            r.incrSeq();
            log.info(r + "对" + target + "发动了[以牙还牙]");
            r.deleteCard(card.getId());
            target.addMessageCard(card);
            fsm.receiveOrder().addPlayerIfHasThreeBlack(target);
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_yi_ya_huan_ya_toc.newBuilder().setCard(card.toPbCard())
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(player1.getAlternativeLocation(target.location())).build());
            }
            r.draw(1);
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(Fsm fsm0) {
        if (!(fsm0 instanceof executeYiYaHuanYa fsm))
            return false;
        Player p = fsm.fsm().inFrontOfWhom();
        Player target = fsm.fsm().whoseTurn();
        if (p == target) {
            target = ThreadLocalRandom.current().nextBoolean() ? target.getNextLeftAlivePlayer() : target.getNextRightAlivePlayer();
            if (p == target) return false;
        }
        final Player finalTarget = target;
        for (Card card : p.getCards().values()) {
            if (card.getColors().contains(Common.color.Black)) {
                GameExecutor.post(p.getGame(), () ->
                        p.getGame().tryContinueResolveProtocol(p, Role.skill_yi_ya_huan_ya_tos.newBuilder().setCardId(card.getId())
                                .setTargetPlayerId(p.getAlternativeLocation(finalTarget.location())).build()), 2, TimeUnit.SECONDS);
                return true;
            }
        }
        return false;
    }
}
