package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_mian_li_cang_zhen_tos extends AbstractProtoHandler<Role.skill_mian_li_cang_zhen_tos> {
    private static final Logger log = Logger.getLogger(skill_mian_li_cang_zhen_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_mian_li_cang_zhen_tos pb) {
        Skill skill = r.findSkill(SkillId.MIAN_LI_CANG_ZHEN);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        skill.executeProtocol(r.getGame(), r, pb);
    }
}
