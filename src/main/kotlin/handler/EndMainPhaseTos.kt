package com.fengsheng.handler

import com.fengsheng.HumanPlayer
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.SendPhaseStart
import com.fengsheng.protos.Fengsheng
import com.fengsheng.skill.MainPhaseSkill
import org.apache.logging.log4j.kotlin.logger

class EndMainPhaseTos : AbstractProtoHandler<Fengsheng.end_main_phase_tos>() {
    override fun handle0(r: HumanPlayer, pb: Fengsheng.end_main_phase_tos) {
        if (!r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        val fsm = r.game!!.fsm as? MainPhaseIdle
        if (r !== fsm?.whoseTurn) {
            logger.error("不是你的回合的出牌阶段")
            r.sendErrorMessage("不是你的回合的出牌阶段")
            return
        }
        if (System.currentTimeMillis() - r.mainPhaseStartTime < 1000) {
            r.sendErrorMessage("为防止误操作，一秒后才能结束出牌阶段")
            return
        }
        if (!r.game!!.mainPhaseAlreadyNotify && r.skills.any { it is MainPhaseSkill && it.mainPhaseNeedNotify(r) }) {
            r.game!!.mainPhaseAlreadyNotify = true
            r.sendErrorMessage("还有未发动的技能，真的要结束出牌阶段吗？")
            return
        }
        r.incrSeq()
        r.game!!.resolve(SendPhaseStart(fsm.whoseTurn))
    }
}
