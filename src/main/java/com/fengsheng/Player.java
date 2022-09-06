package com.fengsheng;

import com.fengsheng.card.Card;
import com.fengsheng.protos.Common;
import com.fengsheng.skill.RoleSkillsData;
import com.fengsheng.skill.Skill;

import java.util.Map;

public interface Player {
    void init(Common.color identity, Common.secret_task secretTask, RoleSkillsData roleSkillsData);

    /**
     * 玩家停止计时器，并且seq值加一
     */
    void incrSeq();

    Game getGame();

    void setGame(Game game);

    /**
     * 玩家在服务器上的座位号，也就是在数组中的index
     */
    int location();

    /**
     * 设置玩家在服务器上的座位号，也就是在数组中的index
     */
    void setLocation(int location);

    /**
     * 根据玩家在服务器上的座位号获取玩家的相对座位号
     */
    int getAbstractLocation(int location);

    /**
     * 根据玩家的相对座位号获取玩家在服务器上的座位号
     */
    int getAlternativeLocation(int location);

    /**
     * 通知有玩家摸牌了
     *
     * @param location     摸牌的玩家在服务器上的座位号
     * @param unknownCount 该玩家摸到的看不到的牌的数量
     * @param cards        该玩家摸到的能看到的卡牌
     */
    void notifyAddHandCard(int location, int unknownCount, Card... cards);

    /**
     * 玩家摸牌
     */
    void draw(int n);

    /**
     * 把卡加入玩家手牌
     */
    void addCard(Card... cards);

    /**
     * 获取玩家手牌
     */
    Map<Integer, Card> getCards();

    /**
     * 从玩家手牌中查找一张牌
     */
    Card findCards(int cardId);

    /**
     * 从玩家手牌中去掉一张牌
     */
    void deleteCard(int cardId);

    /**
     * 玩家删除所有手牌
     *
     * @return 被删除的所有手牌
     */
    Card[] deleteAllCards();

    /**
     * 把卡加入玩家情报
     */
    void addMessageCard(Card... cards);

    /**
     * 获取玩家情报
     */
    Map<Integer, Card> getMessageCards();

    /**
     * 从玩家情报中查找一张牌
     */
    Card findMessageCards(int cardId);

    /**
     * 从玩家情报中去掉一张牌
     */
    void deleteMessageCard(int cardId);

    /**
     * 玩家删除所有情报
     *
     * @return 被删除的所有情报
     */
    Card[] deleteAllMessageCards();

    /**
     * 判断玩家是否有三张同色的情报
     *
     * @param colors 需要判断的颜色
     */
    boolean checkThreeSameMessageCard(Common.color... colors);

    /**
     * 通知进入了某名玩家的摸牌阶段
     */
    void notifyDrawPhase();

    /**
     * 通知进入了某名玩家的出牌阶段
     *
     * @param waitSecond 超时时间
     */
    void notifyMainPhase(int waitSecond);

    /**
     * 通知进入了某名玩家的情报传递阶段开始时
     *
     * @param waitSecond 超时时间
     */
    void notifySendPhaseStart(int waitSecond);

    void notifySendMessageCard(Player player, Player targetPlayer, Player[] lockedPlayers, Card messageCard, Common.direction direction);

    /**
     * 通知进入了某名玩家的情报传递阶段
     *
     * @param waitSecond 超时时间
     */
    void notifySendPhase(int waitSecond);

    /**
     * 通知某名玩家选择接收情报
     */
    void notifyChooseReceiveCard();

    /**
     * 通知进入了某名玩家的争夺阶段
     *
     * @param waitSecond 超时时间
     */
    void notifyFightPhase(int waitSecond);

    /**
     * 通知进入了某名玩家的情报接收阶段
     */
    void notifyReceivePhase();

    /**
     * 通知进入了某名玩家的情报接收阶段
     *
     * @param waitSecond 超时时间
     */
    void notifyReceivePhase(int waitSecond);

    /**
     * 通知某名玩家死亡了
     *
     * @param location 死亡的玩家在服务器上的座位号
     * @param loseGame 是否因为没有手牌可以作为情报传递而输掉游戏导致的死亡
     */
    void notifyDie(int location, boolean loseGame);

    /**
     * 通知胜利
     *
     * @param declareWinners 宣胜的玩家
     * @param winners        胜利的玩家，包含宣胜的玩家
     */
    void notifyWin(Player[] declareWinners, Player[] winners);

    /**
     * 通知有人在濒死求澄清
     *
     * @param whoDie  濒死的玩家
     * @param askWhom 被询问是否使用澄清救人的玩家
     */
    void notifyAskForChengQing(Player whoDie, Player askWhom);

    /**
     * 通知有人正在选择死亡给的三张牌
     *
     * @param whoDie 死亡的玩家
     */
    void waitForDieGiveCard(Player whoDie);

    void setAlive(boolean alive);

    boolean isAlive();

    void setLose(boolean lose);

    boolean isLose();

    /**
     * 设置玩家是否已经失去了身份牌
     */
    void setHasNoIdentity(boolean hasNoIdentity);

    /**
     * 获取玩家是否已经失去了身份牌
     */
    boolean hasNoIdentity();

    void setIdentity(Common.color identity);

    Common.color getIdentity();

    void setSecretTask(Common.secret_task secretTask);

    Common.secret_task getSecretTask();

    void setSkills(Skill[] skills);

    Skill[] getSkills();

    Skill findSkill(int skillHashCode);

    /**
     * 获得玩家的角色
     */
    Common.role getRole();

    /**
     * 获得玩家的角色牌是否面朝上
     */
    boolean isRoleFaceUp();

    /**
     * 增加每回合技能使用次数计数
     */
    void addSkillUseCount(int skillHashCode);

    /**
     * 获取每回合技能使用次数计数
     */
    int getSkillUseCount(int skillHashCode);

    /**
     * 重置每回合技能使用次数计数
     */
    void resetSkillUseCount();

    /**
     * （日志用）将颜色转为角色身份的字符串
     */
    static String identityColorToString(Common.color color) {
        return switch (color) {
            case Red -> "红方";
            case Blue -> "蓝方";
            case Black -> "神秘人";
            default -> throw new RuntimeException("unknown color: " + color);
        };
    }

    /**
     * （日志用）将颜色转为角色身份的字符串
     */
    static String identityColorToString(Common.color color, Common.secret_task task) {
        return switch (color) {
            case Red -> "红方";
            case Blue -> "蓝方";
            case Black -> switch (task) {
                case Killer -> "神秘人[镇压者]";
                case Stealer -> "神秘人[簒夺者]";
                case Collector -> "神秘人[双重间谍]";
                default -> throw new RuntimeException("unknown secret task: " + task);
            };
            default -> throw new RuntimeException("unknown color: " + color);
        };
    }
}
