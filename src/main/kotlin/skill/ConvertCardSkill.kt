package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.card_type

/**
 * 有这个技能的玩家，[cardTypeA]可以/必须当作[cardTypeB]使用
 * @param must `false`表示可以，`true`表示必须
 */
abstract class ConvertCardSkill(val cardTypeA: card_type, val cardTypeB: card_type, val must: Boolean) : Skill {
    open fun onConvert(r: Player) {}
}

/**
 * 判断一个玩家是否能用某张卡牌
 *
 * @param needType 需要的卡牌类型
 * @param actualCard 实际的卡牌
 * @param onlyMust 是否只看“必须”的技能（盛老板发动技能使用别人的牌时，只看“必须”的技能）
 * @see ConvertCardSkill
 */
fun Player.canUseCardTypes(
    needType: card_type,
    actualCard: Card,
    onlyMust: Boolean = false
): Pair<Boolean, ConvertCardSkill?> {
    val actualType = actualCard.type
    var ok = needType == actualType
    var convertCardSkill: ConvertCardSkill? = null
    for (s in skills) {
        if (s is ConvertCardSkill && s.cardTypeA == actualType) {
            if (s.cardTypeB == needType) {
                if (!ok && (!onlyMust || s.must)) {
                    ok = true
                    convertCardSkill = s
                }
            } else if (s.must) return false to null
        }
    }
    return ok to convertCardSkill
}