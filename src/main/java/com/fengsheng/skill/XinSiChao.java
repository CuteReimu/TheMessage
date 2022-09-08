package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * 端木静技能【新思潮】：出牌阶段限一次，你可以弃置一张手牌，然后摸两张牌。
 */
public class XinSiChao extends AbstractSkill {
    private static final Logger log = Logger.getLogger(XinSiChao.class);

    @Override
    public void init(Game g) {

    }

    @Override
    public SkillId getSkillId() {
        return SkillId.XIN_SI_CHAO;
    }

    @Override
    public ResolveResult execute(Game g) {
        return null;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("现在不是出牌阶段空闲时点");
            return;
        }
        if (r.getSkillUseCount(getSkillId()) > 0) {
            log.error("[新思潮]一回合只能发动一次");
            return;
        }
        var pb = (Role.skill_xin_si_chao_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        Card card = r.findCard(pb.getCardId());
        if (card == null) {
            log.error("没有这张卡");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        log.info(r + "发动了[新思潮]");
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_xin_si_chao_toc.newBuilder().setPlayerId(player.getAlternativeLocation(r.location())).build());
        }
        g.playerDiscardCard(r, card);
        r.draw(2);
        g.continueResolve();
    }

    public static boolean ai(MainPhaseIdle e, final Skill skill) {
        if (e.player().getSkillUseCount(SkillId.XIN_SI_CHAO) > 0)
            return false;
        Card card = null;
        for (Card c : e.player().getCards().values()) {
            card = c;
            break;
        }
        if (card == null)
            return false;
        final int cardId = card.getId();
        GameExecutor.post(e.player().getGame(), () -> skill.executeProtocol(
                e.player().getGame(), e.player(), Role.skill_xin_si_chao_tos.newBuilder().setCardId(cardId).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
