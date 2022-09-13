package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import com.fengsheng.skill.RoleSkillsData;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import com.fengsheng.skill.TriggeredSkill;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractPlayer implements Player {
    private static final Logger log = Logger.getLogger(AbstractPlayer.class);

    protected Game game;
    protected int location;
    protected final Map<Integer, Card> cards;
    protected final Map<Integer, Card> messageCards;
    protected Common.color identity;
    protected Common.secret_task secretTask;
    protected final AliveInfo aliveInfo;
    protected RoleSkillsData roleSkillsData;
    protected final Map<SkillId, Integer> skillUseCount;

    public AbstractPlayer() {
        cards = new HashMap<>();
        messageCards = new HashMap<>();
        aliveInfo = new AliveInfo();
        roleSkillsData = new RoleSkillsData();
        skillUseCount = new HashMap<>();
    }

    public AbstractPlayer(AbstractPlayer player) {
        game = player.game;
        cards = player.cards;
        messageCards = player.messageCards;
        location = player.location;
        identity = player.identity;
        secretTask = player.secretTask;
        aliveInfo = player.aliveInfo;
        roleSkillsData = player.roleSkillsData;
        skillUseCount = player.skillUseCount;
    }

    @Override
    public void init(Common.color identity, Common.secret_task secretTask, RoleSkillsData roleSkillsData, RoleSkillsData[] roleSkillsDataArray) {
        log.info(this + "的身份是" + Player.identityColorToString(identity, secretTask));
        this.identity = identity;
        this.secretTask = secretTask;
        if (roleSkillsData != null) {
            this.roleSkillsData = new RoleSkillsData(roleSkillsData);
            for (Skill skill : roleSkillsData.getSkills()) {
                if (skill instanceof TriggeredSkill s) s.init(game);
            }
        } else {
            this.roleSkillsData = new RoleSkillsData();
        }
    }

    @Override
    public void incrSeq() {
    }

    @Override
    public Game getGame() {
        return game;
    }

    @Override
    public void setGame(Game game) {
        this.game = game;
    }

    @Override
    public int location() {
        return location;
    }

    @Override
    public void setLocation(int location) {
        this.location = location;
    }

    @Override
    public int getAbstractLocation(int location) {
        return (location + this.location) % game.getPlayers().length;
    }

    @Override
    public int getAlternativeLocation(int location) {
        location -= this.location;
        if (location < 0) {
            location += game.getPlayers().length;
        }
        return location;
    }

    @Override
    public void draw(int n) {
        Card[] cards = game.getDeck().draw(n);
        addCard(cards);
        log.info(this + "摸了" + Arrays.toString(cards) + "，现在有" + this.cards.size() + "张手牌");
        for (Player player : game.getPlayers()) {
            if (player == this)
                player.notifyAddHandCard(location, 0, cards);
            else
                player.notifyAddHandCard(location, cards.length);
        }
    }

    @Override
    public void addCard(Card... cards) {
        for (Card card : cards) {
            this.cards.put(card.getId(), card);
        }
    }

    @Override
    public Map<Integer, Card> getCards() {
        return cards;
    }

    @Override
    public Card findCard(int cardId) {
        return cards.get(cardId);
    }

    @Override
    public Card deleteCard(int cardId) {
        return cards.remove(cardId);
    }

    @Override
    public Card[] deleteAllCards() {
        Card[] cards = this.cards.values().toArray(new Card[0]);
        this.cards.clear();
        return cards;
    }

    @Override
    public void addMessageCard(Card... cards) {
        for (Card card : cards) {
            this.messageCards.put(card.getId(), card);
        }
    }

    @Override
    public Map<Integer, Card> getMessageCards() {
        return messageCards;
    }

    @Override
    public Card findMessageCard(int cardId) {
        return messageCards.get(cardId);
    }

    @Override
    public Card deleteMessageCard(int cardId) {
        return messageCards.remove(cardId);
    }

    @Override
    public Card[] deleteAllMessageCards() {
        Card[] cards = messageCards.values().toArray(new Card[0]);
        messageCards.clear();
        return cards;
    }

    @Override
    public boolean checkThreeSameMessageCard(Common.color... colors) {
        for (Common.color color : colors) {
            int count = 0;
            for (Card card : messageCards.values()) {
                if (card.getColors().contains(color)) {
                    count++;
                }
            }
            if (count >= 3) return true;
        }
        return false;
    }

    @Override
    public void notifyDying(int location, boolean loseGame) {

    }

    @Override
    public void notifyDie(int location) {
        if (this.location == location) {
            game.playerDiscardCard(this, cards.values().toArray(new Card[0]));
            game.getDeck().discard(messageCards.values().toArray(new Card[0]));
        }
    }

    @Override
    public void setAlive(boolean alive) {
        this.aliveInfo.alive = alive;
    }

    @Override
    public boolean isAlive() {
        return aliveInfo.alive;
    }

    @Override
    public void setLose(boolean lose) {
        this.aliveInfo.lose = lose;
    }

    @Override
    public boolean isLose() {
        return aliveInfo.lose;
    }

    @Override
    public void setHasNoIdentity(boolean hasNoIdentity) {
        this.aliveInfo.hasNoIdentity = hasNoIdentity;
    }

    @Override
    public boolean hasNoIdentity() {
        return aliveInfo.hasNoIdentity;
    }

    @Override
    public Common.color getIdentity() {
        return identity;
    }

    @Override
    public Common.secret_task getSecretTask() {
        return secretTask;
    }

    @Override
    public void setSkills(Skill[] skills) {
        this.roleSkillsData.setSkills(skills);
    }

    @Override
    public Skill[] getSkills() {
        return roleSkillsData.getSkills();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Skill> T findSkill(SkillId skillId) {
        for (Skill skill : getSkills()) {
            if (skill.getSkillId() == skillId) {
                return (T) skill;
            }
        }
        return null;
    }

    @Override
    public Common.role getRole() {
        return roleSkillsData.getRole();
    }

    @Override
    public boolean isRoleFaceUp() {
        return roleSkillsData.isFaceUp();
    }

    public void setRoleFaceUp(boolean faceUp) {
        roleSkillsData.setFaceUp(faceUp);
    }

    @Override
    public void addSkillUseCount(SkillId skillId) {
        skillUseCount.compute(skillId, (k, v) -> v == null ? 1 : v + 1);
    }

    @Override
    public int getSkillUseCount(SkillId skillId) {
        return Objects.requireNonNullElse(skillUseCount.get(skillId), 0);
    }

    @Override
    public void resetSkillUseCount() {
        skillUseCount.clear();
    }

    @Override
    public Player getNextLeftAlivePlayer() {
        int left;
        for (left = location - 1; left != location; left--) {
            if (left < 0) left += game.getPlayers().length;
            if (game.getPlayers()[left].isAlive()) break;
        }
        return game.getPlayers()[left];
    }

    @Override
    public Player getNextRightAlivePlayer() {
        int right;
        for (right = location + 1; right != location; right++) {
            if (right >= game.getPlayers().length) right -= game.getPlayers().length;
            if (game.getPlayers()[right].isAlive()) break;
        }
        return game.getPlayers()[right];
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Player p)) return false;
        return game == p.getGame() && location == p.location();
    }

    private static class AliveInfo {
        boolean alive = true;
        boolean lose = false;
        boolean hasNoIdentity = false;
    }
}
