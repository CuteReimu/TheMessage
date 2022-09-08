package com.fengsheng;

import com.fengsheng.card.*;
import com.fengsheng.phase.*;
import com.fengsheng.protos.Common;
import com.fengsheng.protos.Fengsheng;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import com.fengsheng.skill.XinSiChao;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class RobotPlayer extends AbstractPlayer {
    private static final Logger log = Logger.getLogger(RobotPlayer.class);

    public RobotPlayer() {

    }

    public RobotPlayer(AbstractPlayer player) {
        super(player);
    }

    @Override
    public void notifyAddHandCard(int location, int unknownCount, Card... cards) {

    }

    @Override
    public void notifyDrawPhase() {

    }

    @Override
    public void notifyMainPhase(int waitSecond) {
        var fsm = (MainPhaseIdle) game.getFsm();
        Player player = fsm.player();
        if (location != player.location()) return;
        for (Skill skill : getSkills()) {
            var ai = aiSkillMainPhase.get(skill.getSkillId());
            if (ai != null && ai.apply(fsm, skill)) return;
        }
        if (cards.size() > 1) {
            for (Card card : cards.values()) {
                var ai = aiMainPhase.get(card.getType());
                if (ai != null && ai.apply(fsm, card)) return;
            }
        }
        GameExecutor.post(game, () -> game.resolve(new SendPhaseStart(this)), 2, TimeUnit.SECONDS);
    }

    @Override
    public void notifySendPhaseStart(int waitSecond) {
        var fsm = (SendPhaseStart) game.getFsm();
        if (this != fsm.player()) return;
        GameExecutor.post(game, () -> autoSendMessageCard(this, true), 2, TimeUnit.SECONDS);
    }

    public void notifySendMessageCard(Player player, Player targetPlayer, Player[] lockedPlayers, Card messageCard, Common.direction direction) {

    }

    @Override
    public void notifySendPhase(int waitSecond) {
        final var fsm = (SendPhaseIdle) game.getFsm();
        if (this != fsm.inFrontOfWhom) return;
        for (Card card : cards.values()) {
            var ai = aiSendPhase.get(card.getType());
            if (ai != null && ai.apply(fsm, card)) return;
        }
        GameExecutor.post(game, () -> {
            var colors = fsm.messageCard.getColors();
            boolean certainlyReceive = fsm.isMessageCardFaceUp && colors.size() == 1 && colors.get(0) != Common.color.Black;
            boolean certainlyReject = fsm.isMessageCardFaceUp && colors.size() == 1 && colors.get(0) == Common.color.Black;
            if (certainlyReceive || Arrays.asList(fsm.lockedPlayers).contains(this) || fsm.whoseTurn == this
                    || (!certainlyReject && ThreadLocalRandom.current().nextInt((game.getPlayers().length - 1) * 2) == 0))
                game.resolve(new OnChooseReceiveCard(fsm.whoseTurn, fsm.messageCard, fsm.inFrontOfWhom, fsm.isMessageCardFaceUp));
            else
                game.resolve(new MessageMoveNext(fsm));
        }, 2, TimeUnit.SECONDS);
    }

    @Override
    public void notifyChooseReceiveCard(Player player) {

    }

    @Override
    public void notifyFightPhase(int waitSecond) {
        var fsm = (FightPhaseIdle) game.getFsm();
        if (this != fsm.whoseFightTurn) return;
        for (Card card : cards.values()) {
            var ai = aiFightPhase.get(card.getType());
            if (ai != null && ai.apply(fsm, card)) return;
        }
        GameExecutor.post(game, () -> game.resolve(new FightPhaseNext(fsm)), 2, TimeUnit.SECONDS);
    }

    @Override
    public void notifyReceivePhase() {

    }

    @Override
    public void notifyReceivePhase(Player whoseTurn, Player inFrontOfWhom, Card messageCard, Player waitingPlayer, int waitSecond) {
        if (waitingPlayer != this) return;
        // TODO 需要增加AI
        GameExecutor.TimeWheel.newTimeout(timeout -> game.tryContinueResolveProtocol(this, Fengsheng.end_receive_phase_tos.getDefaultInstance()), 2, TimeUnit.SECONDS);
    }

    @Override
    public void notifyWin(Player[] declareWinners, Player[] winners) {

    }

    @Override
    public void notifyAskForChengQing(Player whoDie, Player askWhom, int waitSecond) {
        var fsm = (WaitForChengQing) game.getFsm();
        if (askWhom != this) return;
        GameExecutor.post(game, () -> game.resolve(new WaitNextForChengQing(fsm)), 2, TimeUnit.SECONDS);
    }

    @Override
    public void waitForDieGiveCard(Player whoDie, int waitSecond) {
        var fsm = (WaitForDieGiveCard) game.getFsm();
        if (whoDie != this) return;
        GameExecutor.post(game, () -> {
            if (identity != Common.color.Black) {
                for (Player target : game.getPlayers()) {
                    if (target != this && target.getIdentity() == identity) {
                        List<Card> giveCards = new ArrayList<>();
                        for (Card card : cards.values()) {
                            giveCards.add(card);
                            if (giveCards.size() >= 3) break;
                        }
                        if (!giveCards.isEmpty()) {
                            var cards = giveCards.toArray(new Card[0]);
                            for (Card card : cards) deleteCard(card.getId());
                            target.addCard(cards);
                            log.info(this + "给了" + target + Arrays.toString(cards));
                            for (Player p : game.getPlayers()) {
                                if (p instanceof HumanPlayer) {
                                    var builder = Fengsheng.notify_die_give_card_toc.newBuilder();
                                    builder.setPlayerId(p.getAlternativeLocation(location));
                                    builder.setTargetPlayerId(p.getAlternativeLocation(target.location()));
                                    if (p == target) {
                                        for (Card card : cards) builder.addCard(card.toPbCard());
                                    } else {
                                        builder.setUnknownCardCount(cards.length);
                                    }
                                    ((HumanPlayer) p).send(builder.build());
                                }
                            }
                        }
                        break;
                    }
                }
            }
            game.resolve(new AfterDieGiveCard(fsm));
        }, 2, TimeUnit.SECONDS);
    }

    /**
     * 随机选择一张牌作为情报传出
     *
     * @param lock 是否考虑锁定
     */
    public static void autoSendMessageCard(Player r, boolean lock) {
        Card card = null;
        for (Card c : r.getCards().values()) {
            card = c;
            break;
        }
        assert card != null;
        Random random = ThreadLocalRandom.current();
        var fsm = (SendPhaseStart) r.getGame().getFsm();
        var dir = card.getDirection();
        if (r.findSkill(SkillId.LIAN_LUO) != null) {
            dir = Common.direction.forNumber(random.nextInt(3));
            assert dir != null;
        }
        int targetLocation = 0;
        List<Integer> availableLocations = new ArrayList<>();
        Player lockedPlayer = null;
        for (Player p : r.getGame().getPlayers()) {
            if (p != r && p.isAlive()) availableLocations.add(p.location());
        }
        if (dir != Common.direction.Up && lock && card.canLock() && random.nextInt(3) != 0) {
            Player player = r.getGame().getPlayers()[availableLocations.get(random.nextInt(availableLocations.size()))];
            if (player.isAlive()) lockedPlayer = player;
        }
        switch (dir) {
            case Up -> {
                targetLocation = availableLocations.get(random.nextInt(availableLocations.size()));
                if (lock && card.canLock() && random.nextBoolean())
                    lockedPlayer = r.getGame().getPlayers()[targetLocation];
            }
            case Left -> {
                targetLocation = (r.location() + r.getGame().getPlayers().length - 1) % r.getGame().getPlayers().length;
                while (!r.getGame().getPlayers()[targetLocation].isAlive()) {
                    targetLocation = (targetLocation + r.getGame().getPlayers().length - 1) % r.getGame().getPlayers().length;
                }
            }
            case Right -> {
                targetLocation = (r.location() + 1) % r.getGame().getPlayers().length;
                while (!r.getGame().getPlayers()[targetLocation].isAlive()) {
                    targetLocation = (targetLocation + 1) % r.getGame().getPlayers().length;
                }
            }
        }
        r.getGame().resolve(new OnSendCard(fsm.player(), card, dir, r.getGame().getPlayers()[targetLocation],
                lockedPlayer == null ? new Player[0] : new Player[]{lockedPlayer}));
    }

    @Override
    public String toString() {
        return location + "号[" + (isRoleFaceUp() ? roleSkillsData.getName() : "机器人") + "]";
    }

    private static final Map<SkillId, BiFunction<MainPhaseIdle, Skill, Boolean>> aiSkillMainPhase = new HashMap<>();
    private static final Map<Common.card_type, BiFunction<MainPhaseIdle, Card, Boolean>> aiMainPhase = new HashMap<>();
    private static final Map<Common.card_type, BiFunction<SendPhaseIdle, Card, Boolean>> aiSendPhase = new HashMap<>();
    private static final Map<Common.card_type, BiFunction<FightPhaseIdle, Card, Boolean>> aiFightPhase = new HashMap<>();

    static {
        aiSkillMainPhase.put(SkillId.XIN_SI_CHAO, XinSiChao::ai);
        aiMainPhase.put(Common.card_type.Cheng_Qing, ChengQing::ai);
        aiMainPhase.put(Common.card_type.Li_You, LiYou::ai);
        aiMainPhase.put(Common.card_type.Ping_Heng, PingHeng::ai);
        aiMainPhase.put(Common.card_type.Shi_Tan, ShiTan::ai);
        aiMainPhase.put(Common.card_type.Wei_Bi, WeiBi::ai);
        aiSendPhase.put(Common.card_type.Po_Yi, PoYi::ai);
        aiFightPhase.put(Common.card_type.Diao_Bao, DiaoBao::ai);
        aiFightPhase.put(Common.card_type.Jie_Huo, JieHuo::ai);
        aiFightPhase.put(Common.card_type.Wu_Dao, WuDao::ai);
    }
}
