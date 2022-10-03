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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.fengsheng.protos.Common.card_type.*;

public class WeiBi extends AbstractCard {
    private static final Logger log = Logger.getLogger(WeiBi.class);

    public WeiBi(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public WeiBi(int id, AbstractCard card) {
        super(id, card);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Wei_Bi;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        if (r == g.getJinBiPlayer()) {
            log.error("你被禁闭了，不能出牌");
            return false;
        }
        Player target = (Player) args[0];
        Common.card_type wantType = (Common.card_type) args[1];
        return WeiBi.canUse(g, r, target, wantType);
    }

    public static boolean canUse(Game g, Player r, Player target, Common.card_type wantType) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("威逼的使用时机不对");
            return false;
        }
        if (r == target) {
            log.error("威逼不能对自己使用");
            return false;
        }
        if (!target.isAlive()) {
            log.error("目标已死亡");
            return false;
        }
        if (!availableCardType.contains(wantType)) {
            log.error("威逼选择的卡牌类型错误：" + wantType);
            return false;
        }
        return true;
    }

    @Override
    public void execute(final Game g, final Player r, Object... args) {
        final Player target = (Player) args[0];
        final Common.card_type wantType = (Common.card_type) args[1];
        log.info(r + "对" + target + "使用了" + this);
        r.deleteCard(this.id);
        WeiBi.execute(this, g, r, target, wantType);
    }

    /**
     * 执行【威逼】的效果
     *
     * @param card 使用的那张【威逼】卡牌。可以为 {@code null} ，因为肥原龙川技能【诡诈】可以视为使用了【威逼】。
     */
    public static void execute(final WeiBi card, final Game g, final Player r, final Player target, final Common.card_type wantType) {
        Fsm resolveFunc = () -> {
            if (target.isRoleFaceUp() && target.findSkill(SkillId.CHENG_FU) != null) {
                log.info(target + "触发了[城府]，威逼无效");
                for (Player player : g.getPlayers()) {
                    if (player instanceof HumanPlayer p) {
                        var builder = Role.skill_cheng_fu_toc.newBuilder().setPlayerId(p.getAlternativeLocation(target.location()));
                        builder.setFromPlayerId(p.getAlternativeLocation(r.location()));
                        if (card != null) builder.setCard(card.toPbCard());
                        p.send(builder.build());
                    }
                }
                if (target.getSkillUseCount(SkillId.JIU_JI) == 1) {
                    target.addSkillUseCount(SkillId.JIU_JI);
                    if (card != null) {
                        target.addCard(card);
                        log.info(target + "将使用的" + card + "加入了手牌");
                        for (Player player : g.getPlayers()) {
                            if (player instanceof HumanPlayer p) {
                                var builder = Role.skill_jiu_ji_b_toc.newBuilder().setPlayerId(p.getAlternativeLocation(target.location()));
                                builder.setCard(card.toPbCard());
                                p.send(builder.build());
                            }
                        }
                    }
                } else if (card != null) {
                    g.getDeck().discard(card);
                }
                return new ResolveResult(new MainPhaseIdle(r), true);
            }
            if (hasCard(target, wantType)) {
                return new ResolveResult(new executeWeiBi(r, target, card, wantType), true);
            } else {
                log.info(target + "向" + r + "展示了所有手牌");
                if (card != null) g.getDeck().discard(card);
                for (Player p : g.getPlayers()) {
                    if (p instanceof HumanPlayer player) {
                        var builder = Fengsheng.wei_bi_show_hand_card_toc.newBuilder();
                        if (card != null) builder.setCard(card.toPbCard());
                        builder.setWantType(wantType);
                        builder.setPlayerId(p.getAlternativeLocation(r.location()));
                        builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                        if (p == r) {
                            for (Card c : target.getCards().values())
                                builder.addCards(c.toPbCard());
                        }
                        player.send(builder.build());
                    }
                }
                return new ResolveResult(new MainPhaseIdle(r), true);
            }
        };
        g.resolve(new OnUseCard(r, r, target, card, Common.card_type.Wei_Bi, r, resolveFunc));
    }

    private static boolean hasCard(Player player, Common.card_type cardType) {
        for (Card card : player.getCards().values()) if (card.getType() == cardType) return true;
        return false;
    }

    private record executeWeiBi(Player r, Player target, WeiBi card,
                                Common.card_type wantType) implements WaitingFsm {
        private static final Logger log = Logger.getLogger(executeWeiBi.class);

        @Override
        public ResolveResult resolve() {
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Fengsheng.wei_bi_wait_for_give_card_toc.newBuilder();
                    if (card != null) builder.setCard(card.toPbCard());
                    builder.setWantType(wantType).setWaitingSecond(20);
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                    if (p == target) {
                        final int seq2 = player.getSeq();
                        builder.setSeq(seq2);
                        player.setTimeout(GameExecutor.post(r.getGame(), () -> {
                            if (player.checkSeq(seq2)) {
                                player.incrSeq();
                                autoSelect();
                                r.getGame().resolve(new MainPhaseIdle(r));
                            }
                        }, player.getWaitSeconds(builder.getWaitingSecond() + 2), TimeUnit.SECONDS));
                    }
                    player.send(builder.build());
                }
            }
            if (target instanceof RobotPlayer) {
                GameExecutor.post(r.getGame(), () -> {
                    autoSelect();
                    r.getGame().resolve(new MainPhaseIdle(r));
                }, 2, TimeUnit.SECONDS);
            }
            return null;
        }

        @Override
        public ResolveResult resolveProtocol(Player player1, GeneratedMessageV3 message) {
            if (!(message instanceof Fengsheng.wei_bi_give_card_tos msg)) {
                log.error("现在正在结算威逼");
                return null;
            }
            if (target != player1) {
                log.error("你不是威逼的目标");
                return null;
            }
            int cardId = msg.getCardId();
            Card c = target.findCard(cardId);
            if (c == null) {
                log.error("没有这张牌");
                return null;
            }
            target.incrSeq();
            log.info(target + "给了" + r + "一张" + c);
            target.deleteCard(cardId);
            r.addCard(c);
            for (Player p : r.getGame().getPlayers()) {
                if (p instanceof HumanPlayer player) {
                    var builder = Fengsheng.wei_bi_give_card_toc.newBuilder();
                    builder.setPlayerId(player.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(player.getAlternativeLocation(target.location()));
                    if (p == r || p == target) builder.setCard(c.toPbCard());
                    player.send(builder.build());
                }
            }
            if (card != null) r.getGame().getDeck().discard(card);
            return new ResolveResult(new MainPhaseIdle(r), true);
        }

        private void autoSelect() {
            List<Integer> availableCards = new ArrayList<>();
            for (Card c : target.getCards().values())
                if (c.getType() == wantType) availableCards.add(c.getId());
            int cardId = availableCards.get(ThreadLocalRandom.current().nextInt(availableCards.size()));
            resolveProtocol(target, Fengsheng.wei_bi_give_card_tos.newBuilder().setCardId(cardId).build());
        }
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "威逼";
    }

    private static final List<Common.card_type> availableCardType = List.of(Cheng_Qing, Jie_Huo, Diao_Bao, Wu_Dao);

    public static boolean ai(MainPhaseIdle e, Card card) {
        Player player = e.player();
        var identity = player.getIdentity();
        List<Player> players = new ArrayList<>();
        for (Player p : player.getGame().getPlayers()) {
            if (p != player && p.isAlive() && !p.getCards().isEmpty() && (identity == Common.color.Black || identity != p.getIdentity())
                    && (!p.isRoleFaceUp() || p.findSkill(SkillId.CHENG_FU) == null)) {
                for (Card c : p.getCards().values())
                    if (availableCardType.contains(c.getType())) players.add(p);
            }
        }
        if (players.isEmpty()) return false;
        Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        List<Common.card_type> cardTypes = new ArrayList<>();
        for (Card c : p.getCards().values())
            if (availableCardType.contains(c.getType())) cardTypes.add(c.getType());
        var cardType = cardTypes.get(ThreadLocalRandom.current().nextInt(cardTypes.size()));
        GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player, p, cardType), 2, TimeUnit.SECONDS);
        return true;
    }
}
