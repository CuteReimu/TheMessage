package com.fengsheng.card;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.protos.Common;

import java.util.Arrays;
import java.util.List;

/**
 * 注意：一张卡牌一定是不能修改的
 */
public interface Card {
    /**
     * 获取卡牌ID。卡牌ID一定大于0
     */
    int getId();

    /**
     * 获取卡牌类型
     */
    Common.card_type getType();

    /**
     * 获取卡牌颜色
     */
    List<Common.color> getColors();

    /**
     * 获取卡牌方向
     */
    Common.direction getDirection();

    /**
     * 获取卡牌作为情报传递时是否可以锁定
     */
    boolean canLock();

    /**
     * 判断卡牌是否能够使用。参数和{@link #execute}的参数相同
     *
     * @param r    使用者
     * @param args 根据不同的卡牌，传入的其他不同参数
     */
    boolean canUse(Game g, Player r, Object... args);

    /**
     * 执行卡牌的逻辑。参数和{@link #canUse}的参数相同
     *
     * @param r    使用者
     * @param args 根据不同的卡牌，传入的其他不同参数
     */
    void execute(final Game g, final Player r, Object... args);

    /**
     * 转换为卡牌的协议对象
     */
    Common.card toPbCard();

    /**
     * （日志用）将颜色转为卡牌的字符串
     */
    static String cardColorToString(List<Common.color> colors) {
        StringBuilder sb = new StringBuilder();
        for (Common.color c : colors) {
            switch (c) {
                case Red -> sb.append("红");
                case Blue -> sb.append("蓝");
                case Black -> sb.append("黑");
            }
        }
        switch (colors.size()) {
            case 1:
                return sb.append("色").toString();
            case 2:
                return sb.append("双色").toString();
        }
        throw new RuntimeException("unknown color: " + Arrays.toString(colors.toArray()));
    }
}
