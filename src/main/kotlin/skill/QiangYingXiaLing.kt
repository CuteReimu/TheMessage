package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.card.Card

/**
 * 孙守謨技能【强硬下令】：你传出的情报均可以锁定。
 */
class QiangYingXiaLing : InitialSkill, SendMessageCanLockSkill {
    override val skillId = SkillId.QIANG_YING_XIA_LING

    override fun checkCanLock(card: Card, lockPlayers: List<Player>): Boolean {
        return lockPlayers.size <= 1
    }
}