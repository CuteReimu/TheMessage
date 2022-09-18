package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.ReceivePhaseSenderSkill;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

/**
 * 邵秀技能【绵里藏针】：你传出的情报被接收后，可以将一张黑色手牌置入接收者的情报区，然后摸一张牌。
 */
public class MianLiCangZhen extends AbstractSkill implements TriggeredSkill {
    @Override
    public void init(Game g) {
        g.addListeningSkill(this);
    }

    @Override
    public SkillId getSkillId() {
        return SkillId.MIAN_LI_CANG_ZHEN;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof ReceivePhaseSenderSkill fsm) || fsm.whoseTurn.findSkill(getSkillId()) == null)
            return null;
        if (fsm.whoseTurn.getSkillUseCount(getSkillId()) > 0)
            return null;
        fsm.whoseTurn.addSkillUseCount(getSkillId());
        return new ResolveResult(new executeMianLiCangZhen(fsm), true);
    }

    private record executeMianLiCangZhen(ReceivePhaseSenderSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeMianLiCangZhen.class);

        @Override
        public ResolveResult resolve() {
            for (Player p : fsm.whoseTurn.getGame().getPlayers())
                p.notifyReceivePhase(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, fsm.whoseTurn, 20);
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
            if (!(message instanceof Role.skill_mian_li_cang_zhen_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.whoseTurn;
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
                log.error("你选择的不是黑色手牌");
                return null;
            }
            Player target = fsm.inFrontOfWhom;
            if (!target.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            r.incrSeq();
            log.info(r + "发动了[绵里藏针]");
            r.deleteCard(card.getId());
            target.addMessageCard(card);
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target);
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_mian_li_cang_zhen_toc.newBuilder().setCard(card.toPbCard())
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(player1.getAlternativeLocation(target.location())).build());
            }
            r.draw(1);
            return new ResolveResult(fsm, true);
        }
    }

    public static boolean ai(Fsm fsm0) {
        if (!(fsm0 instanceof executeMianLiCangZhen fsm))
            return false;
        Player p = fsm.fsm().whoseTurn;
        if (p == fsm.fsm().inFrontOfWhom) return false;
        for (Card card : p.getCards().values()) {
            if (card.getColors().contains(Common.color.Black)) {
                p.getGame().tryContinueResolveProtocol(p, Role.skill_mian_li_cang_zhen_tos.newBuilder().setCardId(card.getId()).build());
                return true;
            }
        }
        return false;
    }
}
