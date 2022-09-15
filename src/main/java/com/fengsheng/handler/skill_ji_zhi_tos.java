package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.ActiveSkill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_ji_zhi_tos extends AbstractProtoHandler<Role.skill_ji_zhi_tos> {
    private static final Logger log = Logger.getLogger(skill_ji_zhi_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_ji_zhi_tos pb) {
        ActiveSkill skill = r.findSkill(SkillId.JI_ZHI);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        skill.executeProtocol(r.getGame(), r, pb);
    }
}
