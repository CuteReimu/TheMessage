package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.SkillId;
import com.google.protobuf.GeneratedMessageV3;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class ShiTan extends AbstractCard {
    private static final Logger log = Logger.getLogger(ShiTan.class);

    private final Common.color[] whoDrawCard;

    public ShiTan(int id, Common.color[] colors, Common.direction direction, boolean lockable, Common.color[] whoDrawCard) {
        super(id, colors, direction, lockable);
        this.whoDrawCard = whoDrawCard;
    }

    public ShiTan(int id, ShiTan card) {
        super(id, card);
        this.whoDrawCard = card.whoDrawCard;
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Shi_Tan;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        Player target = (Player) args[0];
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("试探的使用时机不对");
            return false;
        }
        if (r == target) {
            log.error("试探不能对自己使用");
            return false;
        }
        if (!target.isAlive()) {
            log.error("目标已死亡");
            return false;
        }
        return true;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {
        Player target = (Player) args[0];
        log.info(r + "对" + target + "使用了" + this);
        r.deleteCard(this.id);
        Fsm resolveFunc = () -> {
            if (target.isRoleFaceUp() && target.findSkill(SkillId.CHENG_FU) != null) {
                log.info(target + "触发了[城府]，试探无效");
                for (Player player : g.getPlayers()) {
                    if (player instanceof HumanPlayer p) {
                        var builder = Role.skill_cheng_fu_toc.newBuilder().setPlayerId(p.getAlternativeLocation(target.location()));
                        builder.setFromPlayerId(p.getAlternativeLocation(r.location()));
                        if (p.equals(r) || p.equals(target) || target.getSkillUseCount(SkillId.JIU_JI) != 1)
                            builder.setCard(this.toPbCard());
                        else
                            builder.setUnknownCardCount(1);
                        p.send(builder.build());
                    }
                }
                if (target.getSkillUseCount(SkillId.JIU_JI) == 1) {
                    target.addSkillUseCount(SkillId.JIU_JI);
                    target.addCard(this);
                    log.info(target + "将使用的" + this + "加入了手牌");
                    for (Player player : g.getPlayers()) {
                        if (player instanceof HumanPlayer p) {
                            var builder = Role.skill_jiu_ji_b_toc.newBuilder().setPlayerId(p.getAlternativeLocation(target.location()));
                            if (p.equals(r) || p.equals(target))
                                builder.setCard(this.toPbCard());
                            else
                                builder.setUnknownCardCount(1);
                            p.send(builder.build());
                        }
                    }
                } else {
                    g.getDeck().discard(this);
                }
                return new ResolveResult(new MainPhaseIdle(r), true);
            }
            for (Player p : g.getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Fengsheng.use_shi_tan_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                    if (p == r) builder.setCardId(this.id);
                    player.send(builder.build());
                }
            }
            return new ResolveResult(new executeShiTan(r, target, this), true);
        };
        g.resolve(new OnUseCard(r, r, target, this, r, resolveFunc));
    }

    private boolean checkDrawCard(Player target) {
        for (var i : whoDrawCard) if (i == target.getIdentity()) return true;
        return false;
    }

    private void notifyResult(Player target, boolean draw) {
        for (Player player : target.getGame().getPlayers()) {
            if (player instanceof HumanPlayer p) {
                p.send(Fengsheng.execute_shi_tan_toc.newBuilder()
                        .setPlayerId(p.getAlternativeLocation(target.location())).setIsDrawCard(draw).build());
            }
        }
    }

    private record executeShiTan(Player r, Player target, ShiTan card) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeShiTan.class);

        @Override
        public ResolveResult resolve() {
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Fengsheng.show_shi_tan_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                    builder.setWaitingSecond(20);
                    if (p == target) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2).setCard(card.toPbCard());
                        player.setTimeout(GameExecutor.post(r.getGame(), () -> {
                            if (player.checkSeq(seq2)) {
                                player.incrSeq();
                                autoSelect();
                                r.getGame().resolve(new MainPhaseIdle(r));
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    } else if (p == r) {
                        builder.setCard(card.toPbCard());
                    }
                    player.send(builder.build());
                }
            }
            if (target instanceof RobotPlayer) {
                GameExecutor.post(target.getGame(), () -> {
                    autoSelect();
                    target.getGame().resolve(new MainPhaseIdle(r));
                }, 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player, GeneratedMessageV3 message) {
            if (!(message instanceof Fengsheng.execute_shi_tan_tos msg)) {
                log.error("现在正在结算试探：" + card);
                return null;
            }
            if (target != player) {
                log.error("你不是试探的目标：" + card);
                return null;
            }
            if (card.checkDrawCard(target) || target.getCards().isEmpty()) {
                if (msg.getCardIdCount() != 0) {
                    log.error(target + "被使用" + card + "时不应该弃牌");
                    return null;
                }
            } else {
                if (msg.getCardIdCount() != 1) {
                    log.error(target + "被使用" + card + "时应该弃一张牌");
                    return null;
                }
            }
            player.incrSeq();
            if (card.checkDrawCard(target)) {
                log.info(target + "选择了[摸一张牌]");
                card.notifyResult(target, true);
                target.draw(1);
            } else {
                log.info(target + "选择了[弃一张牌]");
                card.notifyResult(target, false);
                if (msg.getCardIdCount() > 0)
                    target.getGame().playerDiscardCard(target, target.findCard(msg.getCardId(0)));
            }
            return new ResolveResult(new MainPhaseIdle(r), true);
        }

        private void autoSelect() {
            var builder = Fengsheng.execute_shi_tan_tos.newBuilder();
            if (!card.checkDrawCard(target) && !target.getCards().isEmpty()) {
                for (int cardId : target.getCards().keySet()) {
                    builder.addCardId(cardId);
                    break;
                }
            }
            resolveProtocol(target, builder.build());
        }
    }

    public Common.color[] getWhoDrawCard() {
        return whoDrawCard;
    }

    @Override
    public Common.card toPbCard() {
        return Common.card.newBuilder().setCardId(id).setCardDir(direction).setCanLock(lockable).setCardType(getType()).addAllCardColor(colors).addAllWhoDrawCard(Arrays.asList(whoDrawCard)).build();
    }

    @Override
    public String toString() {
        String color = Card.cardColorToString(colors);
        if (whoDrawCard.length == 1)
            return Player.identityColorToString(whoDrawCard[0]) + "+1试探";
        Set<Common.color> set = new HashSet<>(List.of(Common.color.Black, Common.color.Red, Common.color.Blue));
        set.remove(whoDrawCard[0]);
        set.remove(whoDrawCard[1]);
        for (Common.color whoDiscardCard : set) {
            return color + Player.identityColorToString(whoDiscardCard) + "-1试探";
        }
        throw new RuntimeException("impossible whoDrawCard: " + Arrays.toString(whoDrawCard));
    }

    public static boolean ai(MainPhaseIdle e, Card card) {
        Player player = e.player();
        List<Player> players = new ArrayList<>();
        for (Player p : player.getGame().getPlayers())
            if (p != player && p.isAlive() && (!p.isRoleFaceUp() || p.findSkill(SkillId.CHENG_FU) == null))
                players.add(p);
        if (players.isEmpty()) return false;
        Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player, p), 2, TimeUnit.SECONDS);
        return true;
    }
}
