package com.fengsheng.skill

import com.fengsheng.Player

/**
 * 仅在出牌阶段可以使用的技能（结束时需要提醒还未发动）
 */
abstract class MainPhaseSkill : InitialSkill {
    /**
     * 出牌阶段结束是是否需要提醒
     */
    open fun mainPhaseNeedNotify(r: Player) = r.getSkillUseCount(skillId) == 0
}