package com.fengsheng.skill

import com.fengsheng.Fsm
import com.fengsheng.Game
import com.fengsheng.Player
import com.fengsheng.ResolveResult
import com.google.protobuf.GeneratedMessageV3

/**
 * 技能的基类
 */
interface Skill {
    /**
     * 获取技能ID
     */
    val skillId: SkillId
}

/**
 * 玩家初始拥有的技能。
 *
 * 只有[InitialSkill]会被无效。
 *
 * 技能产生出来的二段技能不会被无效，不要继承[InitialSkill]。
 */
interface InitialSkill : Skill

/**
 * 直到回合结束无效的技能
 */
class InvalidSkill private constructor(val originSkill: InitialSkill) : Skill {
    override val skillId = SkillId.INVALID

    companion object {
        fun deal(player: Player) {
            player.skills = player.skills.map { if (it is InitialSkill) InvalidSkill(it) else it }
        }

        fun reset(game: Game) {
            for (player in game.players) {
                val skills = player!!.skills
                if (skills.any { it is InvalidSkill })
                    player.skills = skills.map { if (it is InvalidSkill) it.originSkill else it }
            }
        }
    }
}

/**
 * 仅在出牌阶段可以使用的技能（结束时需要提醒还未发动）
 */
abstract class MainPhaseSkill : ActiveSkill {
    /**
     * 出牌阶段结束是是否需要提醒
     */
    open fun mainPhaseNeedNotify(r: Player) = r.getSkillUseCount(skillId) == 0
}

/**
 * 主动技能，一般是在出牌阶段空闲时点、争夺阶段空闲时点由玩家主动发动的技能
 */
interface ActiveSkill : Skill {
    /**
     * 玩家协议或机器人请求发动技能时调用
     */
    fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3)
}

/**
 * 触发的技能，一般是使用卡牌时、情报接收阶段、死亡前触发的技能
 */
interface TriggeredSkill : Skill {
    /**
     *  * 对于自动发动的技能，判断并发动这个技能时会调用这个函数
     *  * 对于使用卡牌、接收情报、死亡前询问发动的技能，判断并询问这个技能时会调用这个函数
     *
     * @return 如果返回值不为 `null` ，说明满足技能触发的条件，将会进入返回的 [Fsm]
     */
    fun execute(g: Game, askWhom: Player): ResolveResult?
}

/**
 * 只有当前回合有效的技能
 */
interface OneTurnSkill : Skill {
    companion object {
        fun reset(game: Game) {
            for (p in game.players) {
                val skills = p!!.skills
                if (skills.any { it is OneTurnSkill })
                    p.skills = skills.filterNot { it is OneTurnSkill }
            }
        }
    }
}
