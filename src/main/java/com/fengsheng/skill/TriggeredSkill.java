package com.fengsheng.skill;

import com.fengsheng.Fsm;
import com.fengsheng.Game;
import com.fengsheng.ResolveResult;

/**
 * 触发的技能，一般是使用卡牌时、情报接收阶段、死亡前触发的技能
 */
public interface TriggeredSkill extends Skill {
    /**
     * 初始化一场游戏时调用
     */
    default void init(Game g) {
        g.addListeningSkill(this);
    }

    /**
     * <li>对于自动发动的技能，判断并发动这个技能时会调用这个函数</li>
     * <li>对于使用卡牌、接收情报、死亡前询问发动的技能，判断并询问这个技能时会调用这个函数</li>
     *
     * @return 如果返回值不为 {@code null} ，说明满足技能触发的条件，将会进入返回的 {@link Fsm}
     */
    ResolveResult execute(Game g);
}
