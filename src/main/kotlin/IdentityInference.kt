package com.fengsheng

import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.protos.Common.secret_task.*
import kotlin.math.max
import kotlin.math.min

/**
 * AI身份推断系统
 *
 * 该类用于AI玩家推测其他玩家的身份，而不是直接使用真实身份信息。
 * 这让AI的行为更加真实，去除了"透视"能力。
 */
class IdentityInference {

    /**
     * 存储对每个玩家身份的推测概率
     * key: 玩家位置
     * value: 身份概率映射
     */
    private val identityProbabilities = mutableMapOf<Int, MutableMap<color, Double>>()

    /**
     * 存储对神秘人任务的推测概率
     * key: 玩家位置
     * value: 任务概率映射
     */
    private val secretTaskProbabilities = mutableMapOf<Int, MutableMap<secret_task, Double>>()

    /**
     * 初始化玩家身份推测
     * 根据实际游戏配置设置初始概率分布
     */
    fun initializePlayers(playerCount: Int, myLocation: Int, myIdentity: color, game: Game) {
        for (i in 0 until playerCount) {
            if (i == myLocation) continue // 不需要推测自己的身份

            val identityProbs = mutableMapOf<color, Double>()
            val taskProbs = mutableMapOf<secret_task, Double>()

            // 根据实际游戏配置计算身份概率分布
            val (redCount, blueCount, blackCount) = when (playerCount) {
                5 -> Triple(2, 2, 1)
                6 -> Triple(2, 2, 2)
                7 -> Triple(3, 3, 1)
                8 -> Triple(3, 3, 2)
                9 -> Triple(3, 3, 3)
                else -> {
                    // 其他人数按通用规则：(n-1)/2对红蓝，剩余为神秘人
                    val teamSize = (playerCount - 1) / 2
                    Triple(teamSize, teamSize, playerCount - teamSize * 2)
                }
            }

            // 计算除了自己之外的身份分布
            val otherPlayerCount = playerCount - 1
            val (myRedCount, myBlueCount, myBlackCount) = when (myIdentity) {
                Red -> Triple(redCount - 1, blueCount, blackCount)
                Blue -> Triple(redCount, blueCount - 1, blackCount)
                Black -> Triple(redCount, blueCount, blackCount - 1)
                else -> Triple(redCount, blueCount, blackCount)
            }

            // 基于实际分布设置初始概率
            identityProbs[Red] = myRedCount.toDouble() / otherPlayerCount
            identityProbs[Blue] = myBlueCount.toDouble() / otherPlayerCount
            identityProbs[Black] = myBlackCount.toDouble() / otherPlayerCount

            // 基于游戏中实际可能出现的神秘人任务设置概率
            val possibleTasks = game.possibleSecretTasks
            if (possibleTasks.isNotEmpty()) {
                val taskProbability = 1.0 / possibleTasks.size
                possibleTasks.forEach { task ->
                    taskProbs[task] = taskProbability
                }
            } else {
                // 如果没有可能的任务（不应该发生），使用默认分布
                val allTasks = listOf(Killer, Stealer, Collector, Mutator, Pioneer, Disturber, Sweeper)
                allTasks.forEach { task ->
                    taskProbs[task] = 1.0 / allTasks.size
                }
            }

            identityProbabilities[i] = identityProbs
            secretTaskProbabilities[i] = taskProbs
        }
    }

    /**
     * 获取推测的身份（概率最高的）
     */
    fun getInferredIdentity(playerLocation: Int): color {
        val probs = identityProbabilities[playerLocation] ?: return Black
        return probs.maxByOrNull { it.value }?.key ?: Black
    }

    /**
     * 获取推测的神秘人任务
     */
    fun getInferredSecretTask(playerLocation: Int): secret_task {
        val probs = secretTaskProbabilities[playerLocation] ?: return Killer
        return probs.maxByOrNull { it.value }?.key ?: Killer
    }

    /**
     * 获取身份概率
     */
    fun getIdentityProbability(playerLocation: Int, identity: color): Double {
        return identityProbabilities[playerLocation]?.get(identity) ?: 0.0
    }

    /**
     * 基于情报传递颜色更新身份推测
     * 红队倾向于传递红色情报，蓝队倾向于传递蓝色情报
     */
    fun updateBasedOnIntelTransmission(playerLocation: Int, cardColors: List<color>) {
        val probs = identityProbabilities[playerLocation] ?: return

        if (Red in cardColors) {
            // 传递红色情报，增加红队概率
            adjustProbability(probs, Red, 0.15)
        }

        if (Blue in cardColors) {
            // 传递蓝色情报，增加蓝队概率
            adjustProbability(probs, Blue, 0.15)
        }

        if (Black in cardColors && cardColors.size == 1) {
            // 只传递黑色情报，增加神秘人概率
            adjustProbability(probs, Black, 0.08)
        }

        // 如果传递混合颜色，降低对应身份的确定性
        if (cardColors.size > 1 && Red in cardColors && Blue in cardColors) {
            adjustProbability(probs, Red, -0.02)
            adjustProbability(probs, Blue, -0.02)
            adjustProbability(probs, Black, 0.04)
        }
    }

    /**
     * 基于对目标的态度更新身份推测
     * 如果玩家A攻击玩家B，说明A和B可能不是同一阵营
     */
    fun updateBasedOnTargetAttitude(attackerLocation: Int, targetLocation: Int, isAttack: Boolean) {
        val attackerProbs = identityProbabilities[attackerLocation] ?: return
        val targetProbs = identityProbabilities[targetLocation] ?: return

        if (isAttack) {
            // 攻击行为，降低同阵营概率
            for (identity in listOf(Red, Blue)) {
                val attackerIdentityProb = attackerProbs[identity] ?: 0.0
                val targetIdentityProb = targetProbs[identity] ?: 0.0

                if (attackerIdentityProb > 0.3 && targetIdentityProb > 0.3) {
                    // 如果两者都有较高概率是同一身份，降低这种可能性
                    adjustProbability(attackerProbs, identity, -0.05)
                    adjustProbability(targetProbs, identity, -0.05)
                }
            }
        } else {
            // 保护行为，增加同阵营概率
            for (identity in listOf(Red, Blue)) {
                val attackerIdentityProb = attackerProbs[identity] ?: 0.0
                val targetIdentityProb = targetProbs[identity] ?: 0.0

                if (attackerIdentityProb > 0.2 && targetIdentityProb > 0.2) {
                    // 如果两者都有一定概率是同一身份，增加这种可能性
                    adjustProbability(attackerProbs, identity, 0.05)
                    adjustProbability(targetProbs, identity, 0.05)
                }
            }
        }
    }

    /**
     * 基于试探卡牌结果更新身份推测（仅对试探使用者）
     * @param proberLocation 使用试探卡的玩家位置
     * @param targetLocation 试探目标位置
     * @param whoDrawCard 试探卡的whoDrawCard字段，表示哪些身份会摸牌
     * @param didDrawCard 目标是否摸了牌（true摸牌，false弃牌）
     */
    fun updateBasedOnProbeResult(proberLocation: Int, targetLocation: Int, whoDrawCard: List<color>, didDrawCard: Boolean) {
        val targetProbs = identityProbabilities[targetLocation] ?: return

        if (didDrawCard) {
            // 目标摸了牌，说明目标身份一定在whoDrawCard中
            for (identity in listOf(Red, Blue, Black)) {
                if (identity in whoDrawCard) {
                    // 这个身份在摸牌列表中，确定可能是这个身份
                    // 在whoDrawCard中的所有身份平分概率
                    targetProbs[identity] = 1.0 / whoDrawCard.size
                } else {
                    // 这个身份不在摸牌列表中，概率为0
                    targetProbs[identity] = 0.0
                }
            }
        } else {
            // 目标弃了牌，说明目标身份一定不在whoDrawCard中
            val excludedIdentities = whoDrawCard.toSet()
            val possibleIdentities = listOf(Red, Blue, Black).filter { it !in excludedIdentities }

            for (identity in listOf(Red, Blue, Black)) {
                if (identity in excludedIdentities) {
                    // 这个身份在摸牌列表中但目标弃牌了，概率为0
                    targetProbs[identity] = 0.0
                } else {
                    // 这个身份不在摸牌列表中且目标弃牌了，在剩余身份中平分概率
                    targetProbs[identity] = if (possibleIdentities.isNotEmpty()) 1.0 / possibleIdentities.size else 0.0
                }
            }
        }
    }

    /**
     * 基于观察到的试探结果更新关系推测（对观察者）
     * 其他玩家只能看到目标摸牌或弃牌，可以推测使用者和目标的关系
     * @param proberLocation 使用试探卡的玩家位置
     * @param targetLocation 试探目标位置
     * @param didDrawCard 目标是否摸了牌
     */
    fun updateBasedOnObservedProbeResult(proberLocation: Int, targetLocation: Int, didDrawCard: Boolean) {
        // 观察到试探结果可以推测使用者和目标的关系
        // 如果目标摸牌，说明试探结果对目标有利，使用者和目标可能是队友
        // 如果目标弃牌，说明试探结果对目标不利，使用者和目标可能是敌人
        updateBasedOnTargetAttitude(proberLocation, targetLocation, !didDrawCard)
    }

    /**
     * 基于游戏结束后的信息更新（学习用）
     */
    fun updateBasedOnGameEnd(playerLocation: Int, actualIdentity: color, actualTask: secret_task) {
        // 这个方法可以用于后续的学习和模式识别
        // 目前先留空，未来可以实现基于历史数据的学习
    }

    /**
     * 基于卡牌使用模式更新身份推测
     * 不同身份的玩家倾向于使用不同类型的卡牌
     */
    fun updateBasedOnCardUsage(playerLocation: Int, cardType: card_type, targetLocation: Int?, isHostile: Boolean) {
        val probs = identityProbabilities[playerLocation] ?: return

        when (cardType) {
            Wei_Bi -> {
                // 威逼：攻击性卡牌，但需要考虑哑炮技能
                if (targetLocation != null) {
                    // 检查目标是否有哑炮的守口如瓶技能（这里简化处理）
                    // 在实际游戏中，哑炮会使威逼无效
                    if (isHostile) {
                        adjustProbability(probs, Black, 0.03)
                    }
                }
            }
            Wu_Dao, Diao_Bao, Jie_Huo -> {
                // 误导、调包、截获：需要根据情报牌颜色判断敌我关系
                // 这里需要额外的情报颜色信息，暂时简化处理
                if (isHostile) {
                    adjustProbability(probs, Black, 0.02)
                }
            }
            Cheng_Qing -> {
                // 澄清：不一定是保护牌
                // 例如：弃掉蓝方角色面前的蓝黑双色情报是攻击性的
                // 这里需要具体的情报颜色和目标身份信息来准确判断
                // 暂时根据isHostile参数判断
                if (!isHostile && targetLocation != null) {
                    // 被认为是保护性使用
                    for (identity in listOf(Red, Blue)) {
                        val currentProb = probs[identity] ?: 0.0
                        if (currentProb > 0.3) {
                            adjustProbability(probs, identity, 0.03)
                        }
                    }
                } else if (isHostile) {
                    // 被认为是攻击性使用
                    adjustProbability(probs, Black, 0.02)
                }
            }
            Ping_Heng -> {
                // 平衡：需要比较手牌数量来判断
                // 如果让手牌很多的人弃掉所有手牌，这是攻击性的
                // 如果让手牌很少的人补充手牌，这是保护性的
                // 这里需要手牌数量信息，暂时根据isHostile参数判断
                if (!isHostile && targetLocation != null) {
                    // 被认为是保护性使用（帮助手牌少的人）
                    for (identity in listOf(Red, Blue)) {
                        val currentProb = probs[identity] ?: 0.0
                        if (currentProb > 0.3) {
                            adjustProbability(probs, identity, 0.03)
                        }
                    }
                } else if (isHostile) {
                    // 被认为是攻击性使用（迫使手牌多的人弃牌）
                    adjustProbability(probs, Black, 0.02)
                }
            }
            Shi_Tan -> {
                // 试探：可以获得身份信息，各阵营都可能使用
                // 不做特殊的身份倾向调整
            }
            Li_You -> {
                // 利诱：翻开牌堆顶的第一张牌，将其置入目标角色的情报区
                // 如果因此导致了目标角色拥有三张或更多相同颜色的情报，则改为将其加入使用者的手牌
                if (targetLocation != null) {
                    // 需要分析目标玩家的情报情况来判断使用者的身份倾向
                    // 这里需要获取目标玩家的情报状态，暂时简化处理

                    // 1. 如果目标情报为1蓝2黑、1蓝1黑、1蓝0黑等情况，使用利诱倾向于给蓝牌
                    //    这增加了蓝方获胜进度，说明使用者可能是蓝方

                    // 2. 如果目标情报为2蓝2红0黑，给黑色会置入情报区，其他颜色会被拿走
                    //    这是明显的攻击性行为

                    // 3. 对于有特殊技能的角色（如裴玲、鬼脚、王响、白小年），
                    //    使用利诱更可能是帮助他们，暗示队友关系

                    // 暂时根据目标推测身份和使用者推测身份的关系来调整
                    val targetInferredIdentity = getInferredIdentity(targetLocation)

                    // 如果对同色身份使用利诱，增加该身份的概率
                    for (identity in listOf(Red, Blue)) {
                        val userIdentityProb = probs[identity] ?: 0.0
                        val targetIdentityProb = getIdentityProbability(targetLocation, identity)

                        if (userIdentityProb > 0.3 && targetIdentityProb > 0.3) {
                            // 推测是队友关系，使用利诱可能是为了帮助队友
                            adjustProbability(probs, identity, 0.05)
                        }
                    }

                    // 利诱的使用也暗示对神秘人身份的可能性（需要灵活获取资源）
                    adjustProbability(probs, Black, 0.02)
                } else {
                    // 没有指定目标的利诱使用，略微增加神秘人可能性
                    adjustProbability(probs, Black, 0.01)
                }
            }
            Po_Yi -> {
                // 破译：各阵营都可能使用，不做特殊调整
            }
            Mi_Ling -> {
                // 密令：会浪费目标角色一张手牌，有一定攻击性
                if (targetLocation != null) {
                    adjustProbability(probs, Black, 0.02)

                    // 如果对推测的敌人使用，符合攻击性特征
                    val targetInferredIdentity = getInferredIdentity(targetLocation)
                    for (identity in listOf(Red, Blue)) {
                        val userIdentityProb = probs[identity] ?: 0.0
                        val targetIdentityProb = getIdentityProbability(targetLocation, identity)

                        if (userIdentityProb > 0.3 && targetIdentityProb < 0.3) {
                            // 对不同阵营使用密令，增加该身份概率
                            adjustProbability(probs, identity, 0.03)
                        }
                    }
                }
            }
            Diao_Hu_Li_Shan -> {
                // 调虎离山：禁用一名角色出牌或使用技能，明显攻击性
                if (targetLocation != null) {
                    adjustProbability(probs, Black, 0.03)

                    // 攻击性卡牌的使用暗示敌对关系
                    updateBasedOnTargetAttitude(playerLocation, targetLocation, true)
                }
            }
            Yu_Qin_Gu_Zong -> {
                // 欲擒故纵：战术卡牌，各阵营都可能使用
            }
            Feng_Yun_Bian_Huan -> {
                // 风云变幻：对神秘人而言使用会略亏，略微降低神秘人概率
                adjustProbability(probs, Black, -0.02)
            }
            else -> {
                // 其他卡牌类型或未识别类型，不做调整
            }
        }
    }

    /**
     * 基于技能使用更新身份推测
     * 某些技能的使用可以暗示身份信息
     * 这是游戏的核心内容，不同角色的技能使用模式能够暗示其身份倾向
     */
    fun updateBasedOnSkillUsage(playerLocation: Int, skillName: String, targetLocation: Int?) {
        val probs = identityProbabilities[playerLocation] ?: return

        when (skillName) {
            // 红方倾向技能
            "火力支援", "急行军", "奇袭" -> {
                // 这些技能通常红方角色更倾向使用
                adjustProbability(probs, Red, 0.08)
            }

            // 蓝方倾向技能
            "密电破译", "情报分析", "反间计" -> {
                // 这些技能通常蓝方角色更倾向使用
                adjustProbability(probs, Blue, 0.08)
            }

            // 神秘人倾向技能
            "潜伏", "暗杀", "破坏" -> {
                // 这些技能通常神秘人更倾向使用
                adjustProbability(probs, Black, 0.08)
            }

            // 保护性技能
            "庇护", "掩护", "救援" -> {
                if (targetLocation != null) {
                    // 保护性技能的使用暗示队友关系
                    val targetInferredIdentity = getInferredIdentity(targetLocation)

                    // 增加使用者与目标同一阵营的概率
                    for (identity in listOf(Red, Blue)) {
                        val userIdentityProb = probs[identity] ?: 0.0
                        val targetIdentityProb = getIdentityProbability(targetLocation, identity)

                        if (targetIdentityProb > 0.3) {
                            adjustProbability(probs, identity, 0.06)
                        }
                    }
                }
            }

            // 攻击性技能
            "狙击", "轰炸", "突袭" -> {
                if (targetLocation != null) {
                    // 攻击性技能的使用暗示敌对关系
                    updateBasedOnTargetAttitude(playerLocation, targetLocation, true)

                    // 根据目标的推测身份调整使用者的身份概率
                    val targetInferredIdentity = getInferredIdentity(targetLocation)

                    when (targetInferredIdentity) {
                        Red -> {
                            // 攻击红方，更可能是蓝方或神秘人
                            adjustProbability(probs, Blue, 0.05)
                            adjustProbability(probs, Black, 0.03)
                            adjustProbability(probs, Red, -0.08)
                        }
                        Blue -> {
                            // 攻击蓝方，更可能是红方或神秘人
                            adjustProbability(probs, Red, 0.05)
                            adjustProbability(probs, Black, 0.03)
                            adjustProbability(probs, Blue, -0.08)
                        }
                        Black -> {
                            // 攻击神秘人，红蓝双方都可能
                            adjustProbability(probs, Red, 0.04)
                            adjustProbability(probs, Blue, 0.04)
                            adjustProbability(probs, Black, -0.08)
                        }
                        else -> {
                            // 未知身份，不做调整
                        }
                    }
                }
            }

            // 情报相关技能
            "传递情报", "截获情报", "分析情报" -> {
                // 这些技能的使用可以结合情报颜色来判断
                // 如果配合红色情报使用，增加红方概率
                // 如果配合蓝色情报使用，增加蓝方概率
                // 暂时做轻微调整，具体需要结合情报颜色信息
                adjustProbability(probs, Red, 0.02)
                adjustProbability(probs, Blue, 0.02)
                adjustProbability(probs, Black, -0.04)
            }

            // 特殊身份技能
            "卧底行动" -> {
                // 卧底相关技能暗示可能是神秘人
                adjustProbability(probs, Black, 0.10)
            }

            "团队协作" -> {
                // 团队协作技能暗示红蓝阵营
                adjustProbability(probs, Red, 0.05)
                adjustProbability(probs, Blue, 0.05)
                adjustProbability(probs, Black, -0.10)
            }

            // 角色特有技能分析
            "守口如瓶" -> {
                // 哑炮的技能，确定身份
                probs[Red] = 0.0
                probs[Blue] = 0.0
                probs[Black] = 1.0
                // 同时更新任务概率
                val taskProbs = secretTaskProbabilities[playerLocation]
                if (taskProbs != null) {
                    taskProbs.clear()
                    taskProbs[Disturber] = 1.0 // 哑炮是干扰者
                }
            }

            "天网" -> {
                // 特定角色技能，可以确定或强烈暗示身份
                // 根据具体角色调整概率
                adjustProbability(probs, Black, 0.15)
            }

            // 资源管理技能
            "节约", "囤积", "交易" -> {
                // 这些技能神秘人可能更常使用
                adjustProbability(probs, Black, 0.03)
            }

            else -> {
                // 未知技能或通用技能，不做调整
                // 但记录下来用于后续分析
            }
        }

        // 技能使用频率也可以暗示身份
        // 经常使用技能的玩家可能更有经验，或者有特定的身份倾向
        // 这里可以添加基于技能使用频率的分析逻辑
    }

    /**
     * 调整身份概率，确保概率和为1
     */
    private fun adjustProbability(probs: MutableMap<color, Double>, identity: color, delta: Double) {
        val currentProb = probs[identity] ?: 0.0
        val newProb = max(0.01, min(0.98, currentProb + delta))
        probs[identity] = newProb

        // 重新归一化概率
        val totalOther = probs.filterKeys { it != identity }.values.sum()
        val remainingProb = 1.0 - newProb

        if (totalOther > 0) {
            val factor = remainingProb / totalOther
            probs.replaceAll { key, value ->
                if (key == identity) newProb else value * factor
            }
        }
    }

    /**
     * 检查是否是推测的队友
     */
    fun isInferredPartner(myIdentity: color, otherLocation: Int): Boolean {
        if (myIdentity == Black) return false // 神秘人没有队友概念

        val otherIdentity = getInferredIdentity(otherLocation)
        return myIdentity == otherIdentity
    }

    /**
     * 检查是否是推测的队友或自己
     */
    fun isInferredPartnerOrSelf(myLocation: Int, myIdentity: color, otherLocation: Int): Boolean {
        if (myLocation == otherLocation) return true
        return isInferredPartner(myIdentity, otherLocation)
    }

    /**
     * 检查是否是推测的敌人
     */
    fun isInferredEnemy(myLocation: Int, myIdentity: color, otherLocation: Int): Boolean {
        return !isInferredPartnerOrSelf(myLocation, myIdentity, otherLocation)
    }
}
