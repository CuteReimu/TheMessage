package com.fengsheng.skill;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.google.protobuf.GeneratedMessageV3;

public interface Skill {
    /**
     * 初始化一场游戏时调用
     */
    void init(Game g);

    /**
     * <li>对于自动发动的技能，判断并发动这个技能时会调用这个函数</li>
     * <li>对于使用卡牌、接收情报、死亡时询问发动的技能，判断并询问这个技能时会调用这个函数</li>
     */
    void execute(Game g);

    /**
     * 玩家协议或机器人请求发动技能时调用
     */
    void executeProtocol(Game g, Player r, GeneratedMessageV3 message);
}
