package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.Skill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;

public class skill_jiao_ji_b_tos extends AbstractProtoHandler<Role.skill_jiao_ji_b_tos> {
    private static final Logger log = Logger.getLogger(skill_jiao_ji_b_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_jiao_ji_b_tos pb) {
        Skill skill = r.findSkill(SkillId.JIAO_JI);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        if (new HashSet<>(pb.getCardIdsList()).size() != pb.getCardIdsCount()) {
            log.error("卡牌重复" + Arrays.toString(pb.getCardIdsList().toArray(new Integer[0])));
            return;
        }
        r.getGame().tryContinueResolveProtocol(r, pb);
    }
}
