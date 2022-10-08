package com.fengsheng.handler;

import com.fengsheng.HumanPlayer;
import com.fengsheng.protos.Role;
import com.fengsheng.skill.ActiveSkill;
import com.fengsheng.skill.SkillId;
import org.apache.log4j.Logger;

import java.util.Arrays;
import java.util.HashSet;

public class skill_ji_song_tos extends AbstractProtoHandler<Role.skill_ji_song_tos> {
    private static final Logger log = Logger.getLogger(skill_ji_song_tos.class);

    @Override
    protected void handle0(HumanPlayer r, Role.skill_ji_song_tos pb) {
        ActiveSkill skill = r.findSkill(SkillId.JI_SONG);
        if (skill == null) {
            log.error("你没有这个技能");
            return;
        }
        if (new HashSet<>(pb.getCardIdsList()).size() != pb.getCardIdsCount()) {
            log.error("卡牌重复" + Arrays.toString(pb.getCardIdsList().toArray(new Integer[0])));
            return;
        }
        skill.executeProtocol(r.getGame(), r, pb);
    }
}
