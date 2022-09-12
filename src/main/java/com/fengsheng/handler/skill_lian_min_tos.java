package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_lian_min_tos extends AbstractProtoHandler<Role.skill_lian_min_tos> {
    private static final Logger log = Logger.getLogger(skill_lian_min_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_lian_min_tos pb) {
        Skill skill = r.findSkill(SkillId.LIAN_MIN);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
