package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_xin_si_chao_tos extends AbstractProtoHandler<Role.skill_xin_si_chao_tos> {
    private static final Logger log = Logger.getLogger(skill_xin_si_chao_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_xin_si_chao_tos pb) {
        Skill skill = r.findSkill(SkillId.XIN_SI_CHAO);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        skill.executeProtocol(r.getGame(), r, pb);
    }
}
