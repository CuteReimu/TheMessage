package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.DieSkill;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * 老汉技能【如归】：你死亡前，可以将你情报区中的一张情报置入当前回合角色的情报区中。
 */
public class RuGui extends AbstractSkill implements TriggeredSkill {
    @Override
    public void init(Game g) {
        g.addListeningSkill(this);
    }

    @Override
    public SkillId getSkillId() {
        return SkillId.RU_GUI;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof DieSkill fsm) || !fsm.askWhom.equals(fsm.diedQueue.get(fsm.diedIndex)) || fsm.askWhom.findSkill(getSkillId()) == null)
            return null;
        if (fsm.askWhom.equals(fsm.whoseTurn))
            return null;
        if (fsm.askWhom.getSkillUseCount(getSkillId()) > 0)
            return null;
        fsm.askWhom.addSkillUseCount(getSkillId());
        return new ResolveResult(new executeRuGui(fsm), true);
    }

    private record executeRuGui(DieSkill fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeRuGui.class);

        @Override
        public ResolveResult resolve() {
            Player r = fsm.askWhom;
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Role.skill_wait_for_ru_gui_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location())).setWaitingSecond(20);
                    if (p.equals(r)) {
                        final int seq2 = p.getSeq();
                        builder.setSeq(seq2);
                        GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_ru_gui_tos.newBuilder().setEnable(false).setSeq(seq2).build()), builder.getWaitingSecond() + 2, TimeUnit.SECONDS);
                    }
                    p.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer)
                GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_ru_gui_tos.newBuilder().setEnable(false).build()), 2, TimeUnit.SECONDS);
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.askWhom) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_ru_gui_tos pb)) {
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
                return new ResolveResult(fsm, true);
            }
            Card card = r.findMessageCard(pb.getCardId());
            if (card == null) {
                log.error("没有这张卡");
                return null;
            }
            Player target = fsm.whoseTurn;
            if (!target.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            r.incrSeq();
            log.info(r + "发动了[如归]");
            r.deleteMessageCard(card.getId());
            target.addMessageCard(card);
            fsm.receiveOrder.addPlayerIfHasThreeBlack(target);
            log.info(r + "面前的" + card + "移到了" + target + "面前");
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_ru_gui_toc.newBuilder().setCardId(card.getId())
                            .setPlayerId(player1.getAlternativeLocation(r.location())).build());
            }
            return new ResolveResult(fsm, true);
        }
    }
}
