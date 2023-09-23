package com.fengsheng.skill

import com.fengsheng.Player

/**
 * 出牌阶段结束时需要提醒还未发动的技能
 */
abstract class MainPhaseSkill : AbstractSkill() {
    /**
     * 出牌阶段结束是是否需要提醒
     */
    open fun mainPhaseNeedNotify(r: Player) = r.getSkillUseCount(skillId) == 0
}