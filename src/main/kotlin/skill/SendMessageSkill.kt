package com.fengsheng.skill

import com.fengsheng.Player
import com.fengsheng.card.Card
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.direction.Left
import com.fengsheng.protos.Common.direction.Right

/**
 * 传递情报类技能，影响你能传递的方向
 */
interface SendMessageDirectionSkill : Skill {
    fun checkDir(card: Card, dir: direction): Boolean
}

/**
 * 传递情报类技能，影响传出的情报是否能锁定
 */
interface SendMessageCanLockSkill : Skill {
    fun checkCanLock(card: Card, lockPlayers: List<Player>): Boolean
}

/**
 * 传递情报类技能，影响你能传哪张情报
 */
interface SendMessageCardSkill : Skill {
    fun checkSendCard(availableCards: List<Card>, card: Card): Boolean
}

/**
 * 判断玩家是否能传出这张情报
 *
 * @param card 要传出的情报
 * @param availableCards 如果不为`null`，表示可以传出的卡牌列表，需要检查卡牌合理性
 * @param dir 宣言的传出方向
 * @param target 传达的目标
 * @param lockPlayers 锁定的目标
 *
 * @return 错误提示，如果是`null`表示可以传出
 */
fun Player.canSendCard(
    card: Card,
    availableCards: List<Card>?,
    dir: direction,
    target: Player,
    lockPlayers: List<Player>
): String? {
    if (availableCards != null) {
        if (!availableCards.any { it.id == card.id }) return "你不能传出这张情报"
        val checkSendCard = skills.lastOrNull { it is SendMessageCardSkill } as? SendMessageCardSkill
        if (checkSendCard != null && !checkSendCard.checkSendCard(availableCards, card))
            return "你不能传出这张情报"
    }
    val checkCanLock = skills.lastOrNull { it is SendMessageCanLockSkill } as? SendMessageCanLockSkill
    if (checkCanLock != null) {
        if (!checkCanLock.checkCanLock(card, lockPlayers)) return "锁定目标错误"
    } else {
        if (lockPlayers.size > 1) return "最多锁定一个目标"
        if (lockPlayers.isNotEmpty() && !card.canLock()) return "这张情报没有锁定标记"
    }
    if (lockPlayers.any { it === this }) return "不能锁定自己"
    if (lockPlayers.toSet().size != lockPlayers.size) return "锁定目标重复"
    val checkDir = skills.lastOrNull { it is SendMessageDirectionSkill } as? SendMessageDirectionSkill
    if (checkDir != null) {
        if (!checkDir.checkDir(card, dir)) return "方向错误: $dir"
    } else {
        if (dir != card.direction) return "方向错误: $dir"
    }
    if (this === target) return "不能传给自己"
    if (dir == Left && target !== getNextLeftAlivePlayer() || dir == Right && target !== getNextRightAlivePlayer())
        return "不能传给那个人: ${getAlternativeLocation(target.location)}"
    if (!target.alive) return "目标已死亡"
    if (lockPlayers.any { !it.alive }) return "锁定目标已死亡"
    return null
}
