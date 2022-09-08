package com.fengsheng.skill;

public abstract class AbstractSkill implements Skill {
    @Override
    public String toString() {
        return getSkillId().toString();
    }
}
