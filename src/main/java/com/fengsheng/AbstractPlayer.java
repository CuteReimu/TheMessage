package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import com.fengsheng.skill.RoleSkillsData;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import com.fengsheng.skill.TriggeredSkill;
import org.apache.log4j.Logger;

import java.util.*;

public abstract class AbstractPlayer implements Player {
    private static final Logger log = Logger.getLogger(AbstractPlayer.class);

    protected Game game;
    protected String playerName;
    protected int location;
    protected final Map<Integer, Card> cards;
    protected final Map<Integer, Card> messageCards;
    protected Common.color identity;
    protected Common.secret_task secretTask;
    private Common.color originIdentity;
    private Common.secret_task originSecretTask;
    boolean alive = true;
    boolean lose = false;
    protected RoleSkillsData roleSkillsData;
    protected final EnumMap<SkillId, Integer> skillUseCount;

    protected AbstractPlayer() {
        cards = new HashMap<>();
        messageCards = new HashMap<>();
        roleSkillsData = new RoleSkillsData();
        skillUseCount = new EnumMap<>(SkillId.class);
    }

    public void setRoleSkillsData(RoleSkillsData roleSkillsData) {
        this.roleSkillsData = roleSkillsData != null ? new RoleSkillsData(roleSkillsData) : new RoleSkillsData();
    }

    @Override
    public void init() {
        log.info(this + "的身份是" + Player.identityColorToString(identity, secretTask));
        for (Skill skill : roleSkillsData.getSkills()) {
            if (skill instanceof TriggeredSkill s) s.init(game);
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
    public String getPlayerName() {
        return playerName;
    }

    @Override
    public void setPlayerName(String name) {
        playerName = name;
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
    public boolean checkThreeSameMessageCard(Card... cards) {
        int red = 0;
        int blue = 0;
        int black = 0;
        for (Card card : cards) {
            for (Common.color c : card.getColors()) {
                switch (c) {
                    case Red -> red++;
                    case Blue -> blue++;
                    case Black -> black++;
                }
            }
        }
        boolean hasRed = red > 0;
        boolean hasBlue = blue > 0;
        boolean hasBlack = black > 0;
        for (Card card : messageCards.values()) {
            for (Common.color c : card.getColors()) {
                switch (c) {
                    case Red -> red++;
                    case Blue -> blue++;
                    case Black -> black++;
                }
            }
        }
        return (!hasRed || red < 3) && (!hasBlue || blue < 3) && (!hasBlack || black < 3);
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
    public Common.color getIdentity() {
        return identity;
    }

    @Override
    public void setIdentity(Common.color identity) {
        this.identity = identity;
    }

    @Override
    public Common.color getOriginIdentity() {
        return originIdentity;
    }

    @Override
    public void setOriginIdentity(Common.color identity) {
        originIdentity = identity;
    }

    @Override
    public Common.secret_task getSecretTask() {
        return secretTask;
    }

    @Override
    public void setSecretTask(Common.secret_task secretTask) {
        this.secretTask = secretTask;
    }

    @Override
    public Common.secret_task getOriginSecretTask() {
        return originSecretTask;
    }

    @Override
    public void setOriginSecretTask(Common.secret_task secretTask) {
        originSecretTask = secretTask;
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
    public String getRoleName() {
        return roleSkillsData.getName();
    }

    @Override
    public Common.role getRole() {
        return roleSkillsData.getRole();
    }

    @Override
    public boolean isRoleFaceUp() {
        return roleSkillsData.isFaceUp();
    }

    @Override
    public void setRoleFaceUp(boolean faceUp) {
        roleSkillsData.setFaceUp(faceUp);
    }

    @Override
    public boolean isFemale() {
        return roleSkillsData.isFemale();
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

    /**
     * 重置每回合技能使用次数计数
     */
    @Override
    public void resetSkillUseCount(SkillId skillId) {
        skillUseCount.remove(skillId);
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
    public String toString() {
        return location + "号[" + getRoleName() + (isRoleFaceUp() ? "" : "(隐)") + "]";
    }
}
