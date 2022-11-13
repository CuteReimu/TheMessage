package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.ActiveSkill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;

public class skill_du_ji_a_tos extends AbstractProtoHandler<Role.skill_du_ji_a_tos> {
    private static final Logger log = Logger.getLogger(skill_du_ji_a_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_du_ji_a_tos pb) {
        ActiveSkill skill = r.findSkill(SkillId.DU_JI);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        if (new HashSet<>(pb.getTargetPlayerIdsList()).size() != pb.getTargetPlayerIdsCount()) {
            log.error("选择的角色重复" + Arrays.toString(pb.getTargetPlayerIdsList().toArray(new Integer[0])));
            return;
        }
        skill.executeProtocol(r.getGame(), r, pb);
    }
}
