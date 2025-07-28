/**
 * AI身份推断系统说明文档
 * 
 * 本文档展示了AI如何从"透视"模式转换为基于观察的推断模式
 * 
 * ## 问题描述
 * 之前的AI系统直接访问其他玩家的真实身份（identity字段），
 * 这给了AI不现实的"透视"能力，使其行为不够真实。
 * 
 * ## 解决方案
 * 实现了IdentityInference类，让AI基于可观察的信息推测其他玩家的身份：
 * 
 * ### 观察点：
 * 1. **情报传递颜色**：红队倾向传红色，蓝队倾向传蓝色
 * 2. **目标选择**：攻击行为暗示非同阵营，保护行为暗示同阵营  
 * 3. **试探结果**：直接获得身份信息
 * 4. **卡牌使用**：不同身份倾向使用不同类型卡牌
 * 5. **技能发动**：某些技能的使用可以暗示身份
 * 
 * ### 推断机制：
 * - 使用概率分布而非绝对判断
 * - 动态更新推测，越来越准确
 * - 多信号融合，提高推断准确性
 * 
 * ## 使用示例
 * 
 * ```kotlin
 * // 初始化4人游戏，AI是0号位红队
 * val inference = IdentityInference()
 * // 需要传入Game实例进行初始化
 * inference.initializePlayers(4, 0, Red, game)
 * 
 * // 初始状态：身份概率基于实际配置
 * println("玩家1红队概率: ${inference.getIdentityProbability(1, Red)}")
 * 
 * // 观察玩家1传递红色情报
 * inference.updateBasedOnIntelTransmission(1, listOf(Red))
 * // 现在玩家1红队概率增加
 * 
 * // 使用试探卡确认玩家2身份
 * inference.updateBasedOnProbeResult(0, 2, listOf(Red), false)
 * // 现在玩家2身份概率根据试探结果调整
 * 
 * // 推断关系
 * val isPartner = inference.isInferredPartner(Red, 1)
 * ```
 * 
 * ## 核心优势
 * 1. **去除透视**：AI不再直接知道真实身份
 * 2. **更真实**：基于观察推断，类似人类思维
 * 3. **动态学习**：随着游戏进行越来越准确
 * 4. **保持平衡**：不破坏游戏机制，只让AI更公平
 * 5. **向后兼容**：人类玩家体验不受影响
 * 
 * ## 技术细节
 * 
 * ### 概率调整算法
 * ```kotlin
 * private fun adjustProbability(probs: MutableMap<color, Double>, identity: color, delta: Double) {
 *     val currentProb = probs[identity] ?: 0.0
 *     val newProb = max(0.01, min(0.98, currentProb + delta))
 *     probs[identity] = newProb
 *     
 *     // 重新归一化概率，确保总和为1
 *     val totalOther = probs.filterKeys { it != identity }.values.sum()
 *     val remainingProb = 1.0 - newProb
 *     
 *     if (totalOther > 0) {
 *         val factor = remainingProb / totalOther
 *         probs.replaceAll { key, value ->
 *             if (key == identity) newProb else value * factor
 *         }
 *     }
 * }
 * ```
 * 
 * ### 主要修改的文件
 * - `IdentityInference.kt`: 核心推断系统
 * - `Player.kt`: 添加推断方法（isInferredPartner等）
 * - `MessageCardValue.kt`: 使用推测身份替换真实身份
 * - `RobotPlayer.kt`: 观察游戏行为并更新推断
 * - `ShiTan.kt`: 试探卡结果更新推断
 * 
 * ## 测试覆盖
 * - 身份推断系统初始化测试
 * - 基于情报传递的更新测试
 * - 基于目标态度的更新测试  
 * - 基于试探结果的更新测试
 * - 推测关系判断测试
 * 
 * 所有测试通过，确保系统稳定可靠。
 */
package com.fengsheng

// 这个文件作为文档说明，展示AI身份推断系统的工作原理和优势