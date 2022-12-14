package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.ReceivePhaseSenderSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * 白菲菲技能【怜悯】：你传出的非黑色情报被接收后，可以从你或接收者的情报区选择一张黑色情报加入你的手牌。
 */
public class LianMin extends AbstractSkill implements TriggeredSkill {
    @Override
    public SkillId getSkillId() {
        return SkillId.LIAN_MIN;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof ReceivePhaseSenderSkill fsm) || fsm.whoseTurn.findSkill(getSkillId()) == null)
            return null;
        if (fsm.messageCard.getColors().contains(Common.color.Black))
            return null;
        if (fsm.whoseTurn.getSkillUseCount(getSkillId()) > 0)
            return null;
        fsm.whoseTurn.addSkillUseCount(getSkillId());
        return new ResolveResult(new executeLianMin(fsm), true);
    }

    private record executeLianMin(ReceivePhaseSenderSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeLianMin.class);

        @Override
        public ResolveResult resolve() {
            for (Player p : fsm.whoseTurn.getGame().getPlayers())
                p.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.whoseTurn, 15);
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.whoseTurn) {
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
            if (!(message instanceof Role.skill_lian_min_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.whoseTurn;
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (pb.getTargetPlayerId() < 0 || pb.getTargetPlayerId() >= r.getGame().getPlayers().length) {
                log.error("目标错误");
                return null;
            }
            Player target = r.getGame().getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
            if (target != r && target != fsm.inFrontOfWhom) {
                log.error("只能以自己或者情报接收者为目标");
                return null;
            }
            if (!target.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            Card card = target.findMessageCard(pb.getCardId());
            if (card == null) {
                log.error("没有这张卡");
                return null;
            }
            if (!card.getColors().contains(Common.color.Black)) {
                log.error("你选择的不是黑色情报");
                return null;
            }
            r.incrSeq();
            log.info(r + "发动了[怜悯]");
            target.deleteMessageCard(card.getId());
            r.addCard(card);
            log.info(target + "面前的" + card + "加入了" + r + "的手牌");
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_lian_min_toc.newBuilder().setCardId(card.getId())
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(player1.getAlternativeLocation(target.location())).build());
            }
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(Fsm fsm0) {
        if (!(fsm0 instanceof executeLianMin fsm))
            return false;
        Player p = fsm.fsm().whoseTurn;
        for (Player target : new Player[]{p, fsm.fsm().inFrontOfWhom}) {
            if (!target.isAlive()) continue;
            for (Card card : target.getMessageCards().values()) {
                if (card.getColors().contains(Common.color.Black)) {
                    GameExecutor.post(p.getGame(), () ->
                            p.getGame().tryContinueResolveProtocol(p, Role.skill_lian_min_tos.newBuilder().setCardId(card.getId())
                                    .setTargetPlayerId(p.getAlternativeLocation(target.location())).build()), 2, TimeUnit.SECONDS);
                    return true;
                }
            }
        }
        return false;
    }
}
