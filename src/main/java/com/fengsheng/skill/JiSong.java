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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 鬼脚技能【急送】：争夺阶段限一次，你可以弃置两张手牌，或从你的情报区弃置一张非黑色情报，然后将待收情报移至一名角色面前。
 */
public class JiSong extends AbstractSkill implements ActiveSkill {
    private static final Logger log = Logger.getLogger(JiSong.class);

    @Override
    public SkillId getSkillId() {
        return SkillId.JI_SONG;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {
        if (!(g.getFsm() instanceof FightPhaseIdle fsm) || r != fsm.whoseFightTurn) {
            log.error("现在不是发动[急送]的时机");
            return;
        }
        if (r.getSkillUseCount(getSkillId()) > 0) {
            log.error("[急送]一回合只能发动一次");
            return;
        }
        var pb = (Role.skill_ji_song_tos) message;
        if ((r instanceof HumanPlayer humanPlayer) && !humanPlayer.checkSeq(pb.getSeq())) {
            log.error("操作太晚了, required Seq: " + humanPlayer.getSeq() + ", actual Seq: " + pb.getSeq());
            return;
        }
        if (pb.getTargetPlayerId() < 0 || pb.getTargetPlayerId() >= g.getPlayers().length) {
            log.error("目标错误");
            return;
        }
        Player target = g.getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
        if (!target.isAlive()) {
            log.error("目标已死亡");
            return;
        }
        if (target == fsm.inFrontOfWhom) {
            log.error("情报本来就在他面前");
            return;
        }
        Card messageCard = null;
        Card[] cards = new Card[2];
        if (pb.getCardIdsCount() == 0 && pb.getMessageCard() != 0) {
            if ((messageCard = r.findMessageCard(pb.getMessageCard())) == null) {
                log.error("没有这张牌");
                return;
            } else if (messageCard.getColors().contains(Common.color.Black)) {
                log.error("这张牌不是非黑色");
                return;
            }
        } else if (pb.getCardIdsCount() == 2 && pb.getMessageCard() == 0) {
            for (int i = 0; i < 2; i++) {
                if ((cards[i] = r.findCard(pb.getCardIds(i))) == null) {
                    log.error("没有这张牌");
                    return;
                }
            }
        } else {
            log.error("发动技能支付的条件不正确");
            return;
        }
        r.incrSeq();
        r.addSkillUseCount(getSkillId());
        if (messageCard != null) {
            log.info(r + "发动了[急送]，弃掉了面前的" + messageCard + "，将情报移至" + target + "面前");
            r.deleteMessageCard(messageCard.getId());
        } else {
            log.info(r + "发动了[急送]，选择弃掉两张手牌，将情报移至" + target + "面前");
            g.playerDiscardCard(r, cards);
        }
        fsm.inFrontOfWhom = target;
        for (Player p : g.getPlayers()) {
            if (p instanceof HumanPlayer player) {
                var builder = Role.skill_ji_song_toc.newBuilder();
                builder.setPlayerId(player.getAlternativeLocation(r.location()));
                builder.setTargetPlayerId(player.getAlternativeLocation(target.location()));
                if (messageCard != null) builder.setMessageCard(messageCard.toPbCard());
                player.send(builder.build());
            }
        }
        fsm.whoseFightTurn = fsm.inFrontOfWhom;
        g.continueResolve();
    }

    public static boolean ai(FightPhaseIdle e, final ActiveSkill skill) {
        final Player player = e.whoseFightTurn;
        if (player.getCards().size() < 2)
            return false;
        var colors = e.messageCard.getColors();
        if (colors.size() != 1)
            return false;
        if (colors.get(0) == Common.color.Black) {
            if (e.inFrontOfWhom != player)
                return false;
        } else {
            Common.color identity = player.getIdentity();
            Common.color identity2 = e.inFrontOfWhom.getIdentity();
            if (identity != Common.color.Black && identity == identity2)
                return false;
            if (identity2 == Common.color.Black || colors.get(0) != identity2)
                return false;
        }
        List<Player> players = new ArrayList<>();
        for (Player p : player.getGame().getPlayers()) {
            if (p != e.inFrontOfWhom && p.isAlive())
                players.add(p);
        }
        if (players.isEmpty())
            return false;
        Random random = ThreadLocalRandom.current();
        final Player target = players.get(random.nextInt(players.size()));
        final Card[] cards = new Card[2];
        int i = 0;
        for (Card c : player.getCards().values()) {
            cards[i++] = c;
            if (i >= 2) break;
        }
        GameExecutor.post(player.getGame(), () -> skill.executeProtocol(
                player.getGame(), player, Role.skill_ji_song_tos.newBuilder().addCardIds(cards[0].getId()).addCardIds(cards[1].getId())
                        .setTargetPlayerId(player.getAlternativeLocation(target.location())).build()
        ), 2, TimeUnit.SECONDS);
        return true;
    }
}
