package com.fengsheng.card;

import com.fengsheng.*;
import com.fengsheng.phase.MainPhaseIdle;
import com.fengsheng.phase.OnUseCard;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class LiYou extends Card {
    private static final Logger log = Logger.getLogger(LiYou.class);

    public LiYou(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        super(id, colors, direction, lockable);
    }

    public LiYou(int id, Card card) {
        super(id, card);
    }

    /**
     * 仅用于“作为利诱使用”
     */
    LiYou(Card originCard) {
        super(originCard);
    }

    @Override
    public Common.card_type getType() {
        return Common.card_type.Li_You;
    }

    @Override
    public boolean canUse(Game g, Player r, Object... args) {
        if (r == g.getJinBiPlayer()) {
            log.error("你被禁闭了，不能出牌");
            return false;
        }
        Player target = (Player) args[0];
        return LiYou.canUse(g, r, target);
    }

    public static boolean canUse(Game g, Player r, Player target) {
        if (!(g.getFsm() instanceof MainPhaseIdle fsm) || r != fsm.player()) {
            log.error("利诱的使用时机不对");
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
        final Player target = (Player) args[0];
        log.info(r + "对" + target + "使用了" + this);
        r.deleteCard(this.id);
        LiYou.execute(this, g, r, target);
    }

    /**
     * 执行【利诱】的效果
     *
     * @param card 使用的那张【利诱】卡牌。可以为 {@code null} ，因为肥原龙川技能【诡诈】可以视为使用了【利诱】。
     */
    public static void execute(LiYou card, final Game g, final Player r, final Player target) {
        Fsm resolveFunc = () -> {
            Card[] deckCards = g.getDeck().draw(1);
            boolean joinIntoHand = false;
            if (deckCards.length > 0) {
                if (target.checkThreeSameMessageCard(deckCards[0])) {
                    joinIntoHand = true;
                    r.addCard(deckCards);
                    log.info(Arrays.toString(deckCards) + "加入了" + r + "的手牌");
                } else {
                    target.addMessageCard(deckCards);
                    log.info(Arrays.toString(deckCards) + "加入了" + target + "的情报区");
                }
            }
            for (Player player : g.getPlayers()) {
                if (player instanceof HumanPlayer p) {
                    var builder = Fengsheng.use_li_you_toc.newBuilder();
                    builder.setPlayerId(p.getAlternativeLocation(r.location()));
                    builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                    if (card != null) builder.setLiYouCard(card.toPbCard());
                    builder.setJoinIntoHand(joinIntoHand);
                    if (deckCards.length > 0) builder.setMessageCard(deckCards[0].toPbCard());
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
            } else {
                if (card != null) g.getDeck().discard(card.getOriginCard());
            }
            return new ResolveResult(new MainPhaseIdle(r), true);
        };
        if (card != null)
            g.resolve(new OnUseCard(r, r, target, card, Common.card_type.Li_You, r, resolveFunc));
        else
            g.resolve(resolveFunc);
    }

    @Override
    public String toString() {
        return Card.cardColorToString(colors) + "利诱";
    }

    public static boolean ai(MainPhaseIdle e, Card card) {
        Player player = e.player();
        List<Player> players = new ArrayList<>();
        for (Player p : player.getGame().getPlayers())
            if (p.isAlive()) players.add(p);
        if (players.isEmpty()) return false;
        Player p = players.get(ThreadLocalRandom.current().nextInt(players.size()));
        GameExecutor.post(player.getGame(), () -> card.execute(player.getGame(), player, p), 2, TimeUnit.SECONDS);
        return true;
    }
}
