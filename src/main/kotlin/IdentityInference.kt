package com.fengsheng

import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.card_type.*
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
     * 基于试探卡牌结果更新身份推测
     * @param proberLocation 使用试探卡的玩家位置
     * @param targetLocation 试探目标位置
     * @param whoDrawCard 试探卡的whoDrawCard字段，表示哪些身份会摸牌
     * @param didDrawCard 目标是否摸了牌（true摸牌，false弃牌）
     */
    fun updateBasedOnProbeResult(proberLocation: Int, targetLocation: Int, whoDrawCard: List<color>, didDrawCard: Boolean) {
        val targetProbs = identityProbabilities[targetLocation] ?: return
        
        if (didDrawCard) {
            // 目标摸了牌，说明目标身份在whoDrawCard中
            for (identity in listOf(Red, Blue, Black)) {
                if (identity in whoDrawCard) {
                    // 这个身份在摸牌列表中，增加概率
                    adjustProbability(targetProbs, identity, 0.3)
                } else {
                    // 这个身份不在摸牌列表中，大幅降低概率
                    adjustProbability(targetProbs, identity, -0.4)
                }
            }
        } else {
            // 目标弃了牌，说明目标身份不在whoDrawCard中
            for (identity in listOf(Red, Blue, Black)) {
                if (identity in whoDrawCard) {
                    // 这个身份在摸牌列表中但目标弃牌了，大幅降低概率
                    adjustProbability(targetProbs, identity, -0.4)
                } else {
                    // 这个身份不在摸牌列表中且目标弃牌了，增加概率
                    adjustProbability(targetProbs, identity, 0.3)
                }
            }
        }
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
                // 利诱：通常是为了获得特定卡牌，各阵营都可能使用
                // 略微增加神秘人可能性（因为他们更需要灵活获取资源）
                adjustProbability(probs, Black, 0.01)
            }
            Po_Yi, Mi_Ling, Diao_Hu_Li_Shan, Yu_Qin_Gu_Zong, Feng_Yun_Bian_Huan -> {
                // 破译、密令、调虎离山、欲擒故纵、风云变幻：高级战术卡牌
                // 需要根据具体使用情况判断，暂时不做调整
            }
            else -> {
                // 其他卡牌类型或未识别类型，不做调整
            }
        }
    }
    
    /**
     * 基于技能使用更新身份推测
     * 某些技能的使用可以暗示身份信息
     */
    fun updateBasedOnSkillUsage(playerLocation: Int, skillName: String, targetLocation: Int?) {
        val probs = identityProbabilities[playerLocation] ?: return
        
        // 不同技能的使用模式可以暗示身份倾向
        // 这里可以根据具体技能的特性来调整概率
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