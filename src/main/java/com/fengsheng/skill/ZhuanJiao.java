package com.fengsheng.skill;

import com.fengsheng.*;
import com.fengsheng.card.Card;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Role;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 白小年技能【转交】：你使用一张手牌后，可以从你的情报区选择一张非黑色情报，将其置入另一名角色的情报区，然后你摸两张牌。你不能通过此技能让任何角色收集三张或更多同色情报。
 */
public class ZhuanJiao extends AbstractSkill implements TriggeredSkill {
    @Override
    public void init(Game g) {
        g.addListeningSkill(this);
    }

    @Override
    public SkillId getSkillId() {
        return SkillId.ZHUAN_JIAO;
    }

    @Override
    public ResolveResult execute(Game g) {
        if (!(g.getFsm() instanceof OnUseCard fsm) || fsm.askWhom != fsm.player || fsm.askWhom.findSkill(getSkillId()) == null)
            return null;
        boolean notBlack = false;
        for (Card card : fsm.askWhom.getMessageCards().values()) {
            if (!card.getColors().contains(Common.color.Black)) {
                notBlack = true;
                break;
            }
        }
        if (!notBlack)
            return null;
        if (fsm.askWhom.getSkillUseCount(getSkillId()) > 0)
            return null;
        fsm.askWhom.addSkillUseCount(getSkillId());
        final Fsm oldResolveFunc = fsm.resolveFunc;
        fsm.resolveFunc = () -> {
            fsm.askWhom.resetSkillUseCount(getSkillId());
            return oldResolveFunc.resolve();
        };
        return new ResolveResult(new executeZhuanJiao(fsm), true);
    }

    private record executeZhuanJiao(OnUseCard fsm) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeZhuanJiao.class);

        @Override
        public ResolveResult resolve() {
            Player r = fsm.askWhom;
            for (Player player : r.getGame().getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Role.skill_wait_for_zhuan_jiao_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location())).setWaitingSecond(20);
                    if (p == r) {
                        final int seq2 = p.getSeq();
                        builder.setSeq(seq2);
                        p.setTimeout(GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_zhuan_jiao_tos.newBuilder().setEnable(false).setSeq(seq2).build()), p.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    p.send(builder.build());
                }
            }
            if (r instanceof RobotPlayer) {
                Card messageCard = null;
                for (Card card : r.getMessageCards().values()) {
                    if (!card.getColors().contains(Common.color.Black)) {
                        messageCard = card;
                        break;
                    }
                }
                if (messageCard != null) {
                    final Card finalCard = messageCard;
                    List<Player> players = new ArrayList<>();
                    Common.color identity = r.getIdentity();
                    Common.color color = messageCard.getColors().get(0);
                    for (Player p : r.getGame().getPlayers()) {
                        if (p == r || !p.isAlive())
                            continue;
                        if (identity == Common.color.Black) {
                            if (color == p.getIdentity())
                                continue;
                        } else {
                            if (p.getIdentity() != Common.color.Black && p.getIdentity() != identity)
                                continue;
                        }
                        int count = 0;
                        for (Card c : p.getMessageCards().values()) {
                            if (c.getColors().contains(color))
                                count++;
                        }
                        if (count < 2)
                            players.add(p);
                    }
                    if (!players.isEmpty()) {
                        Player target = players.get(ThreadLocalRandom.current().nextInt(players.size()));
                        GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_zhuan_jiao_tos.newBuilder()
                                .setTargetPlayerId(r.getAlternativeLocation(target.location()))
                                .setEnable(true).setCardId(finalCard.getId()).build()), 2, TimeUnit.SECONDS);
                        return null;
                    }
                }
                GameExecutor.post(r.getGame(), () -> r.getGame().tryContinueResolveProtocol(r, Role.skill_zhuan_jiao_tos.newBuilder().setEnable(false).build()), 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (player != fsm.askWhom) {
                log.error("不是你发技能的时机");
                return null;
            }
            if (!(message instanceof Role.skill_zhuan_jiao_tos pb)) {
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
            if (card.getColors().contains(Common.color.Black)) {
                log.error("不是非黑色情报");
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
            Player target = r.getGame().getPlayers()[r.getAbstractLocation(pb.getTargetPlayerId())];
            if (!target.isAlive()) {
                log.error("目标已死亡");
                return null;
            }
            int count = 0;
            for (Card c : target.getMessageCards().values()) {
                if (c.getColors().contains(card.getColors().get(0)))
                    count++;
            }
            if (count >= 2) {
                log.error("你不能通过此技能让任何角色收集三张或更多同色情报");
                return null;
            }
            r.incrSeq();
            log.info(r + "发动了[转交]");
            r.deleteMessageCard(card.getId());
            target.addMessageCard(card);
            log.info(r + "面前的" + card + "移到了" + target + "面前");
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player1)
                    player1.send(Role.skill_zhuan_jiao_toc.newBuilder().setCardId(card.getId())
                            .setPlayerId(player1.getAlternativeLocation(r.location()))
                            .setTargetPlayerId(player1.getAlternativeLocation(target.location())).build());
            }
            r.draw(2);
            return new ResolveResult(fsm, true);
        }
    }
}
