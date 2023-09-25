package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.protos.Common.card_type

/**
 * 有这个技能的玩家，[cardTypeA]可以/必须当作[cardTypeB]使用
 * @param must `false`表示可以，`true`表示必须
 */
abstract class ConvertCardSkill(val cardTypeA: card_type, val cardTypeB: card_type, val must: Boolean) : Skill {
    open fun onConvert(r: Player) {}
}

fun Player.canUseCardTypes(needType: card_type, actualType: card_type): Pair<Boolean, ConvertCardSkill?> {
    var ok = needType == actualType
    var convertCardSkill: ConvertCardSkill? = null
    for (s in skills) {
        if (s is ConvertCardSkill && s.cardTypeA == actualType) {
            if (s.cardTypeB == needType) {
                if (!ok) {
                    ok = true
                    convertCardSkill = s
                }
            } else if (s.must) return false to null
        }
    }
    return ok to convertCardSkill
}