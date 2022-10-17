package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.DieSkill;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * 李宁玉技能【遗信】：你死亡前，可以将一张手牌置入另一名角色的情报区。
 */
public class YiXin extends AbstractSkill implements TriggeredSkill {
    @Override
    public SkillId getSkillId() {
        return SkillId.YI_XIN;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof DieSkill fsm) || !fsm.askWhom.equals(fsm.diedQueue.get(fsm.diedIndex)) || fsm.askWhom.findSkill(getSkillId()) == null)
            return null;
        if (!fsm.askWhom.isRoleFaceUp())
            return null;
        if (fsm.askWhom.getCards().isEmpty())
            return null;
        if (fsm.askWhom.getSkillUseCount(getSkillId()) > 0)
            return null;
        fsm.askWhom.addSkillUseCount(getSkillId());
        return new ResolveResult(new executeYiXin(fsm), true);
    }

    private record executeYiXin(DieSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeYiXin.class);

        @Override
        public ResolveResult resolve() {
            Player r = fsm.askWhom;
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Role.skill_wait_for_yi_xin_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location())).setWaitingSecond(15);
                    if (p.equals(r)) {
                        final int seq2 = p.getSeq();
                        builder.setSeq(seq2);
                        GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_yi_xin_tos.newBuilder().setEnable(false).setSeq(seq2).build()), p.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS);
                    }
                    p.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                if (fsm.whoseTurn.isAlive() && r != fsm.whoseTurn) {
                    for (Card card : r.getCards().values()) {
                        GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_yi_xin_tos.newBuilder().setEnable(true).setCardId(card.getId())
                                .setTargetPlayerId(r.getAlternativeLocation(fsm.whoseTurn.location())).build()), 2, TimeUnit.SECONDS);
                        return null;
                    }
                }
                GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_yi_xin_tos.newBuilder().setEnable(false).build()), 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.askWhom) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_yi_xin_tos pb)) {
                log.error("错误的协议");
                return null;
            }
            Player r = fsm.askWhom;
            Game g = r.getGame();
            if (r instanceof HumanPlayer humanPlayer && !humanPlayer.checkSeq(pb.getSeq())) {
                log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
                return null;
            }
            if (!pb.getEnable()) {
                r.incrSeq();
                for (Player p : g.getPlayers()) {
                    if (p instanceof HumanPlayer player1)
                        player1.send(Role.skill_yi_xin_toc.newBuilder().setEnable(false).build());
                }
                return new ResolveResult(fsm, true);
            }
            Card card = r.findCard(pb.getCardId());
            if (card == null) {
                log.error("没有这张卡");
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
            r.incrSeq();
            log.info(r + "发动了[遗信]");
            r.deleteCard(card.getId());
            target.addMessageCard(card);
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target);
            log.info(r + "将" + card + "放置在" + target + "面前");
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_yi_xin_toc.newBuilder().setEnable(true).setCard(card.toPbCard())
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(player1.getAlternativeLocation(target.location())).build());
            }
            return new ResolveResult(fsm, true);
        }
    }
}
