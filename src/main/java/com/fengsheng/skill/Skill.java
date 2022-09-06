package com.fengsheng.skill;

import com.fengsheng.Fsm;
import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.google.protobuf.GeneratedMessageV3;

public interface Skill {
    /**
     * 初始化一场游戏时调用
     */
    void init(Game g);

    /**
     * 获取技能ID
     */
    SkillId getSkillId();

    /**
     * <li>对于自动发动的技能，判断并发动这个技能时会调用这个函数</li>
     * <li>对于使用卡牌、接收情报、死亡时询问发动的技能，判断并询问这个技能时会调用这个函数</li>
     *
     * @return 如果返回值不为 {@code null} ，说明满足技能触发的条件，将会进入返回的 {@link Fsm}
     */
    ResolveResult execute(Game g);

    /**
     * 玩家协议或机器人请求发动技能时调用
     */
    void executeProtocol(Game g, Player r, GeneratedMessageV3 message);
}
