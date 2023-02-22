package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.skill.ActiveSkill
import com.fengsheng.skill.SkillId

com.fengsheng.protos.Role
import java.util.concurrent.LinkedBlockingQueue
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.*
class skill_ji_song_tos : AbstractProtoHandler<Role.skill_ji_song_tos>() {
    override fun handle0(r: HumanPlayer, pb: Role.skill_ji_song_tos) {
        val skill = r.findSkill<ActiveSkill>(SkillId.JI_SONG)
        if (skill == null) {
            log.error("你没有这个技能")
            return
        }
        if (HashSet(pb.cardIdsList).size != pb.cardIdsCount) {
            log.error("卡牌重复" + Arrays.toString(pb.cardIdsList.toTypedArray()))
            return
        }
        skill.executeProtocol(r.game, r, pb)
    }

    companion object {
        private val log = Logger.getLogger(skill_ji_song_tos::class.java)
    }
}