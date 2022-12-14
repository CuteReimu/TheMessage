package com.fengsheng.skill;

import com.fengsheng.Game;
import com.fengsheng.GameExecutor;
import com.fengsheng.HumanPlayer;
import com.fengsheng.Player;
import com.fengsheng.card.Card;
import com.fengsheng.phase.FightPhaseIdle;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * 韩梅技能【移花接木】：争夺阶段，你可以翻开此角色牌，然后从一名角色的情报区选择一张情报，将其置入另一名角色的情报区，若如此做会让其收集三张或更多同色情报，则改为将该情报加入你的手牌。
 */
public class YiHuaJieMu extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(YiHuaJieMu.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.YI_HUA_JIE_MU;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("[移花接木]的使用时机不对");
            return;
        }
        if (r.isRoleFaceUp()) {
            log.error("你现在正面朝上，不能发动[移花接木]");
            return;
        }
        var pb = (Role.skill_yi_hua_jie_mu_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (pb.getFromPlayerId() < 0 || pb.getFromPlayerId() >= g.getPlayers().length) {
            log.error("目标错误");
            return;
        }
        Player fromPlayer = r.getGame().getPlayers()[r.getAbstractLocation(pb.getFromPlayerId())];
        if (!fromPlayer.isAlive()) {
            log.error("目标已死亡");
            return;
        }
        if (pb.getToPlayerId() < 0 || pb.getToPlayerId() >= g.getPlayers().length) {
            log.error("目标错误");
            return;
        }
        Player toPlayer = r.getGame().getPlayers()[r.getAbstractLocation(pb.getToPlayerId())];
        if (!toPlayer.isAlive()) {
            log.error("目标已死亡");
            return;
        }
        if (pb.getFromPlayerId() == pb.getToPlayerId()) {
            log.error("选择的两个目标不能相同");
            return;
        }
        Card card = fromPlayer.findMessageCard(pb.getCardId());
        if (card == null) {
            log.error("没有这张卡");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        g.playerSetRoleFaceUp(r, true);
        log.info(r + "发动了[移花接木]");
        fromPlayer.deleteMessageCard(card.getId());
        boolean joinIntoHand = false;
        if (toPlayer.checkThreeSameMessageCard(card)) {
            joinIntoHand = true;
            r.addCard(card);
            log.info(fromPlayer + "面前的" + card + "加入了" + r + "的手牌");
        } else {
            toPlayer.addMessageCard(card);
            log.info(fromPlayer + "面前的" + card + "加入了" + toPlayer + "的情报区");
        }
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player)
                player.send(Role.skill_yi_hua_jie_mu_toc.newBuilder().setCardId(card.getId()).setJoinIntoHand(joinIntoHand)
                        .setPlayerId(player.getAlternativeLocation(r.location()))
                        .setFromPlayerId(player.getAlternativeLocation(fromPlayer.location()))
                        .setToPlayerId(player.getAlternativeLocation(toPlayer.location())).build());
        }
        fsm.whoseFightTurn = fsm.inFrontOfWhom;
        g.continueResolve();
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        Player p = e.whoseFightTurn;
        if (p.isRoleFaceUp())
            return false;
        Card blackCard = null;
        for (Card card : p.getMessageCards().values()) {
            if (card.getColors().size() == 1 && card.getColors().get(0) == Common.color.Black) {
                blackCard = card;
                break;
            }
        }
        if (blackCard == null) return false;
        final Card targetCard = blackCard;
        Player target = e.whoseTurn;
        if (p == target || !target.isAlive()) return false;
        if (p.getIdentity() == Common.color.Black || p.getIdentity() != target.getIdentity()) {
            int black = 0;
            for (Card card : target.getMessageCards().values()) {
                if (card.getColors().contains(Common.color.Black)) black++;
            }
            if (black < 2) {
                GameExecutor.post(e.whoseFightTurn.getGame(), () -> skill.executeProtocol(
                        e.whoseFightTurn.getGame(), e.whoseFightTurn, Role.skill_yi_hua_jie_mu_tos.newBuilder().setCardId(targetCard.getId())
                                .setFromPlayerId(0).setToPlayerId(p.getAlternativeLocation(target.location())).build()
                ), 2, TimeUnit.SECONDS);
                return true;
            }
        }
        return false;
    }
}
