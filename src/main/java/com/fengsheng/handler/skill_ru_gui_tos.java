package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_ru_gui_tos extends AbstractProtoHandler<Role.skill_ru_gui_tos> {
    private static final Logger log = Logger.getLogger(skill_ru_gui_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_ru_gui_tos pb) {
        Skill skill = r.findSkill(SkillId.RU_GUI);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
