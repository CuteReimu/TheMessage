package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

public class skill_miao_bi_qiao_bian_b_tos extends AbstractProtoHandler<Role.skill_miao_bi_qiao_bian_b_tos> {
    private static final Logger log = Logger.getLogger(skill_miao_bi_qiao_bian_b_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_miao_bi_qiao_bian_b_tos pb) {
        Skill skill = r.findSkill(SkillId.MIAO_BI_QIAO_BIAN);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
