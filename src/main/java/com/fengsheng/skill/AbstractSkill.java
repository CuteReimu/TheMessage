package com.fengsheng.skill;

public abstract class AbstractSkill implements Skill {
    private final int hash;
    private final String name;

    protected AbstractSkill(String skillName) {
        name = skillName;
        hash = skillName.hashCode();
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return name;
    }
}
