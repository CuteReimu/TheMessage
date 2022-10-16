package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.EnumSet;

public class skill_qiang_ling_tos extends AbstractProtoHandler<Role.skill_qiang_ling_tos> {
    private static final Logger log = Logger.getLogger(skill_qiang_ling_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_qiang_ling_tos pb) {
        Skill skill = r.findSkill(SkillId.QIANG_LING);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        if (EnumSet.copyOf(pb.getTypesList()).size() != pb.getTypesCount()) {
            log.error("宣言的卡牌类型重复" + Arrays.toString(pb.getTypesValueList().toArray(new Integer[0])));
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
