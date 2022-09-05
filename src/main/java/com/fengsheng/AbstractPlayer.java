package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import com.fengsheng.skill.RoleSkillsData;
import com.fengsheng.skill.Skill;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractPlayer implements Player {
    private static final Logger log = Logger.getLogger(AbstractPlayer.class);

    protected Game game;
    protected int location;
    protected final Map<Integer, Card> cards = new HashMap<>();
    protected final Map<Integer, Card> messageCards = new HashMap<>();
    protected Common.color identity;
    protected Common.secret_task secretTask;
    protected boolean alive = true;
    protected boolean lose = false;
    protected boolean hasNoIdentity = false;
    protected RoleSkillsData roleSkillsData;
    protected final Map<Integer, Integer> skillUseCount = new HashMap<>();

    public AbstractPlayer() {

    }

    public AbstractPlayer(AbstractPlayer player) {
        game = player.game;
        location = player.location;
        identity = player.identity;
        secretTask = player.secretTask;
        alive = player.alive;
        lose = player.lose;
        hasNoIdentity = player.hasNoIdentity;
        roleSkillsData = player.roleSkillsData;
    }

    @Override
    public void init(Common.color identity, Common.secret_task secretTask, RoleSkillsData roleSkillsData) {
        log.info(this + "的身份是" + Player.identityColorToString(identity, secretTask));
        this.identity = identity;
        this.secretTask = secretTask;
        if (roleSkillsData != null) {
            this.roleSkillsData = new RoleSkillsData(roleSkillsData);
            for (Skill skill : roleSkillsData.getSkills()) {
                skill.init(game);
            }
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
        log.info(this + "摸了" + Arrays.toString(cards) + "，现在有" + cards.length + "张手牌");
        for (Player player : game.getPlayers()) {
            if (player == this)
                notifyAddHandCard(location, 0, cards);
            else
                notifyAddHandCard(location, cards.length);
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
    public Card findCards(int cardId) {
        return cards.get(cardId);
    }

    @Override
    public void deleteCard(int cardId) {
        cards.remove(cardId);
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
    public Card findMessageCards(int cardId) {
        return messageCards.get(cardId);
    }

    @Override
    public void deleteMessageCard(int cardId) {
        messageCards.remove(cardId);
    }

    @Override
    public Card[] deleteMessageAllCards() {
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
    public void setAlive(boolean alive) {
        this.alive = alive;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public void setLose(boolean lose) {
        this.lose = lose;
    }

    @Override
    public boolean isLose() {
        return lose;
    }

    @Override
    public void setHasNoIdentity(boolean hasNoIdentity) {
        this.hasNoIdentity = hasNoIdentity;
    }

    @Override
    public boolean hasNoIdentity() {
        return hasNoIdentity;
    }

    @Override
    public void setIdentity(Common.color identity) {
        this.identity = identity;
    }

    @Override
    public Common.color getIdentity() {
        return identity;
    }

    @Override
    public void setSecretTask(Common.secret_task secretTask) {
        this.secretTask = secretTask;
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

    @Override
    public Skill findSkill(int skillHashCode) {
        for (Skill skill : roleSkillsData.getSkills()) {
            if (skill.hashCode() == skillHashCode) {
                return skill;
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
        return roleSkillsData != null && roleSkillsData.isFaceUp();
    }

    @Override
    public void addSkillUseCount(int skillHashCode) {
        skillUseCount.compute(skillHashCode, (k, v) -> v == null ? 1 : v + 1);
    }

    @Override
    public int getSkillUseCount(int skillHashCode) {
        return Objects.requireNonNullElse(skillUseCount.get(skillHashCode), 0);
    }

    @Override
    public void resetSkillUseCount() {
        skillUseCount.clear();
    }
}
