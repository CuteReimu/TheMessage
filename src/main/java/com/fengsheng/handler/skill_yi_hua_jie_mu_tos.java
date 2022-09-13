package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.ActiveSkill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_yi_hua_jie_mu_tos extends AbstractProtoHandler<Role.skill_yi_hua_jie_mu_tos> {
    private static final Logger log = Logger.getLogger(skill_yi_hua_jie_mu_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_yi_hua_jie_mu_tos pb) {
        ActiveSkill skill = r.findSkill(SkillId.YI_HUA_JIE_MU);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        skill.executeProtocol(r.getGame(), r, pb);
    }
}
