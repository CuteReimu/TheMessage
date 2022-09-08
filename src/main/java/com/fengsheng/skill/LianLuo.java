package com.fengsheng.skill;

import com.fengsheng.Game;
import com.fengsheng.Player;
import com.fengsheng.ResolveResult;
import com.google.protobuf.GeneratedMessageV3;

/**
 * 老鳖技能【联络】：你传递情报时，可以将箭头视为任意方向。
 */
public class LianLuo extends AbstractSkill {
    @Override
    public void init(Game g) {

    }

    @Override
    public SkillId getSkillId() {
        return SkillId.LIAN_LUO;
    }

    @Override
    public ResolveResult execute(Game g) {
        return null;
    }

    @Override
    public void executeProtocol(Game g, Player r, GeneratedMessageV3 message) {

    }
}
