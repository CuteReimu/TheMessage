package com.fengsheng.skill;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.google.protobuf.GeneratedMessageV3;

/**
 * 主动技能，一般是在出牌阶段空闲时点、争夺阶段空闲时点由玩家主动发动的技能
 */
public interface ActiveSkill extends Skill {
    /**
     * 玩家协议或机器人请求发动技能时调用
     */
    void executeProtocol(Game g, Player r, GeneratedMessageV3 message);
}
