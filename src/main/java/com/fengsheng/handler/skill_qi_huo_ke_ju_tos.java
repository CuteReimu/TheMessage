package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_qi_huo_ke_ju_tos extends AbstractProtoHandler<Role.skill_qi_huo_ke_ju_tos> {
    private static final Logger log = Logger.getLogger(skill_qi_huo_ke_ju_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_qi_huo_ke_ju_tos pb) {
        Skill skill = r.findSkill(SkillId.QI_HUO_KE_JU);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
