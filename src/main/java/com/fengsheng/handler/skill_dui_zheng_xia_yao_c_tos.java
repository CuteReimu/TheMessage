package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_dui_zheng_xia_yao_c_tos extends AbstractProtoHandler<Role.skill_dui_zheng_xia_yao_c_tos> {
    private static final Logger log = Logger.getLogger(skill_dui_zheng_xia_yao_c_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_dui_zheng_xia_yao_c_tos pb) {
        Skill skill = r.findSkill(SkillId.DUI_ZHENG_XIA_YAO);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
