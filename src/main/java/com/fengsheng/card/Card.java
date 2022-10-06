package com.fengsheng.card;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.protos.Common;

import java.util.Arrays;
import java.util.List;

/**
 * 注意：一张卡牌一定是不能修改的
 */
public abstract class Card {
    protected final int id;
    protected final List<Common.color> colors;
    protected final Common.direction direction;
    protected final boolean lockable;
    private final Card originCard;

    protected Card(int id, Common.color[] colors, Common.direction direction, boolean lockable) {
        this.id = id;
        this.colors = List.of(colors);
        this.direction = direction;
        this.lockable = lockable;
        originCard = null;
    }

    protected Card(int id, Card card) {
        this.id = id;
        this.colors = card.colors;
        this.direction = card.direction;
        this.lockable = card.lockable;
        originCard = null;
    }

    /**
     * 仅用于“作为……使用”
     */
    Card(Card card) {
        id = card.id;
        this.colors = card.colors;
        this.direction = card.direction;
        this.lockable = card.lockable;
        originCard = card;
    }

    /**
     * 获取卡牌ID。卡牌ID一定大于0
     */
    public int getId() {
        return id;
    }

    /**
     * 获取卡牌类型
     */
    public abstract Common.card_type getType();

    /**
     * 获取卡牌颜色
     */
    public List<Common.color> getColors() {
        return colors;
    }

    /**
     * 获取卡牌方向
     */
    public Common.direction getDirection() {
        return direction;
    }

    /**
     * 获取卡牌作为情报传递时是否可以锁定
     */
    public boolean canLock() {
        return lockable;
    }

    /**
     * 获取原本的卡牌
     *
     * @return 如果原本卡牌就是自己，则返回自己。如果是“作为……使用”，则返回原卡牌。
     */
    Card getOriginCard() {
        return originCard == null ? this : originCard;
    }

    /**
     * 判断卡牌是否能够使用。参数和{@link #execute}的参数相同
     *
     * @param r    使用者
     * @param args 根据不同的卡牌，传入的其他不同参数
     */
    public abstract boolean canUse(Game g, Player r, Object... args);

    /**
     * 执行卡牌的逻辑。参数和{@link #canUse}的参数相同
     *
     * @param r    使用者
     * @param args 根据不同的卡牌，传入的其他不同参数
     */
    public abstract void execute(Game g, Player r, Object... args);

    public boolean hasSameColor(Card card2) {
        for (Common.color color1 : this.getColors()) {
            if (card2.getColors().contains(color1)) {
                return true;
            }
        }
        return false;
    }

    public Common.card toPbCard() {
        Card c = this.getOriginCard();
        return Common.card.newBuilder().setCardId(c.id).setCardDir(c.direction).setCanLock(c.lockable).setCardType(c.getType()).addAllCardColor(c.colors).build();
    }

    /**
     * （日志用）将颜色转为卡牌的字符串
     */
    public static String cardColorToString(List<Common.color> colors) {
        StringBuilder sb = new StringBuilder();
        for (Common.color c : colors) {
            switch (c) {
                case Red -> sb.append("红");
                case Blue -> sb.append("蓝");
                case Black -> sb.append("黑");
            }
        }
        switch (colors.size()) {
            case 1 -> sb.append("色");
            case 2 -> sb.append("双色");
            default -> throw new RuntimeException("unknown color: " + Arrays.toString(colors.toArray()));
        }
        return sb.toString();
    }

    public static Card falseCard(Common.card_type falseType, Card originCard) {
        return switch (falseType) {
            case Cheng_Qing -> new ChengQing(originCard);
            case Wei_Bi -> new WeiBi(originCard);
            case Li_You -> new LiYou(originCard);
            case Ping_Heng -> new PingHeng(originCard);
            case Po_Yi -> new PoYi(originCard);
            case Jie_Huo -> new JieHuo(originCard);
            case Diao_Bao -> new DiaoBao(originCard);
            case Wu_Dao -> new WuDao(originCard);
            default -> throw new IllegalStateException("Unexpected value: " + falseType);
        };
    }
}
