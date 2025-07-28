package com.fengsheng

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
     * 游戏开始时，所有身份的概率相等
     */
    fun initializePlayers(playerCount: Int, myLocation: Int, myIdentity: color) {
        for (i in 0 until playerCount) {
            if (i == myLocation) continue // 不需要推测自己的身份
            
            val identityProbs = mutableMapOf<color, Double>()
            val taskProbs = mutableMapOf<secret_task, Double>()
            
            // 初始身份概率（除了自己的身份）
            when (myIdentity) {
                Red -> {
                    identityProbs[Red] = 0.33   // 可能是队友
                    identityProbs[Blue] = 0.33  // 可能是敌人
                    identityProbs[Black] = 0.34 // 可能是神秘人
                }
                Blue -> {
                    identityProbs[Red] = 0.33   // 可能是敌人
                    identityProbs[Blue] = 0.33  // 可能是队友
                    identityProbs[Black] = 0.34 // 可能是神秘人
                }
                Black -> {
                    identityProbs[Red] = 0.4    // 红方
                    identityProbs[Blue] = 0.4   // 蓝方
                    identityProbs[Black] = 0.2  // 其他神秘人
                }
                else -> {
                    identityProbs[Red] = 0.33
                    identityProbs[Blue] = 0.33
                    identityProbs[Black] = 0.34
                }
            }
            
            // 初始神秘人任务概率
            val tasks = listOf(Killer, Stealer, Collector, Mutator, Pioneer, Disturber, Sweeper)
            tasks.forEach { task ->
                taskProbs[task] = 1.0 / tasks.size
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
     */
    fun updateBasedOnProbeResult(proberLocation: Int, targetLocation: Int, isRedTeam: Boolean) {
        val targetProbs = identityProbabilities[targetLocation] ?: return
        
        if (isRedTeam) {
            // 试探结果是红队
            targetProbs[Red] = 0.9
            targetProbs[Blue] = 0.05
            targetProbs[Black] = 0.05
        } else {
            // 试探结果不是红队
            targetProbs[Red] = 0.1
            targetProbs[Blue] = 0.45
            targetProbs[Black] = 0.45
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
    fun updateBasedOnCardUsage(playerLocation: Int, cardType: String, targetLocation: Int?, isHostile: Boolean) {
        val probs = identityProbabilities[playerLocation] ?: return
        
        when (cardType) {
            "威逼", "误导", "调包" -> {
                // 攻击性卡牌使用
                if (isHostile) {
                    // 使用攻击性卡牌，slightly increase chance of being mysterious person
                    adjustProbability(probs, Black, 0.03)
                }
            }
            "澄清", "平衡" -> {
                // 保护性卡牌使用
                if (!isHostile && targetLocation != null) {
                    // 使用保护性卡牌，increase team identity probability
                    for (identity in listOf(Red, Blue)) {
                        val currentProb = probs[identity] ?: 0.0
                        if (currentProb > 0.3) {
                            adjustProbability(probs, identity, 0.05)
                        }
                    }
                }
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