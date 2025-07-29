package com.fengsheng

import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.skill.SkillId
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
     * 游戏引用，用于访问游戏状态
     */
    private lateinit var game: Game

    /**
     * 初始化玩家身份推测
     * 根据实际游戏配置设置初始概率分布
     */
    fun initializePlayers(playerCount: Int, myLocation: Int, myIdentity: color, game: Game) {
        this.game = game
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
    fun updateBasedOnSkillUsage(playerLocation: Int, skillId: SkillId, targetLocation: Int?) {
        val probs = identityProbabilities[playerLocation] ?: return

        when (skillId) {
            // 确定身份的技能（基于角色唯一技能）
            SkillId.SHOU_KOU_RU_PING -> {
                // 守口如瓶：哑炮独有技能，该技能触发时双方各摸一张牌
                // 暗示友好关系，增加两人是队友的概率
                if (targetLocation != null) {
                    // 守口如瓶触发时双方各摸牌，这是友好信号，增加同队概率
                    analyzeCooperativeSkillUsage(playerLocation, targetLocation, probs, 0.15)
                }
            }
            SkillId.HAN_HOU_LAO_SHI -> {
                // 憨厚老实：哑炮技能 - 被动触发，无法控制，因此不能说明身份
                // 该技能是被动触发的，玩家无法控制，无法用于身份推断
            }

            // 基于行为的身份推断技能
            SkillId.BO_AI -> {
                // 博爱：白沧浪技能 - 摸牌然后可以给其他角色一张手牌
                // 如果给了手牌，增加两人是队友的概率
                if (targetLocation != null) {
                    analyzeCooperativeSkillUsage(playerLocation, targetLocation, probs, 0.1)
                }
            }

            SkillId.JIU_JI -> {
                // 就计：李宁玉技能 - 被试探/威逼/利诱后可以摸两张牌并收回那张牌
                // 纯粹的自我保护和资源获取技能，无明确阵营倾向
                // 不做身份推断调整
            }

            SkillId.YI_HUA_JIE_MU -> {
                // 移花接木：韩梅技能 - 移动场上情报
                // 阵营倾向取决于移动了什么情报、从哪移到哪
                if (targetLocation != null) {
                    // 需要额外的情报移动信息才能准确判断
                    // 暂时基于目标关系做轻微调整
                    analyzeContextualSkillUsage(playerLocation, targetLocation, probs, 0.05)
                }
            }

            SkillId.LIAN_LUO -> {
                // 联络：老鳖技能 - 传递情报时可以改变箭头方向
                // 不涉及目标选择，无法从技能使用推断身份关系
            }

            SkillId.GUI_ZHA -> {
                // 诡诈：肥原龙川技能 - 视为对指定角色使用威逼或利诱
                // 根据使用的卡牌类型（威逼或利诱）来判断敌我关系
                // 具体分析应该基于实际使用的是威逼还是利诱，以及目标状态
                if (targetLocation != null) {
                    // 诡诈技能会使用威逼或利诱，我们可以根据目标关系推断使用的卡牌类型
                    val mostLikelyIdentity = probs.keys.maxByOrNull { probs[it] ?: 0.0 } ?: Black
                    val isLikelyWeiBi = !isInferredPartnerOrSelf(playerLocation, mostLikelyIdentity, targetLocation)
                    val isLikelyLiYou = !isLikelyWeiBi

                    if (isLikelyWeiBi) {
                        // 推测使用的是威逼，调用威逼的分析逻辑
                        updateBasedOnCardUsage(playerLocation, Wei_Bi, targetLocation, true)
                    } else {
                        // 推测使用的是利诱，调用利诱的分析逻辑
                        updateBasedOnCardUsage(playerLocation, Li_You, targetLocation, false)
                    }

                    // 诡诈技能本身也暗示一定的身份倾向
                    analyzeContextualSkillUsage(playerLocation, targetLocation, probs, 0.03)
                }
            }

            SkillId.JIN_SHEN -> {
                // 谨慎：金生火技能 - 接收双色情报后可以用手牌交换
                // 自我保护技能，无明确阵营倾向
            }

            SkillId.LIAN_MIN -> {
                // 怜悯：白菲菲技能 - 保护性质
                if (targetLocation != null) {
                    analyzeProtectiveSkillUsage(playerLocation, targetLocation, probs, 0.1)
                }
            }

            SkillId.YI_YA_HUAN_YA -> {
                // 以牙还牙：王魁技能 - 报复性质
                if (targetLocation != null) {
                    analyzeAggressiveSkillUsage(playerLocation, targetLocation, probs, 0.1)
                }
            }

            SkillId.JIE_DAO_SHA_REN -> {
                // 借刀杀人：商玉技能 - 强制他人对第三方使用牌
                if (targetLocation != null) {
                    analyzeAggressiveSkillUsage(playerLocation, targetLocation, probs, 0.1)
                }
            }

            SkillId.JIAO_JI -> {
                // 交际：裴玲技能 - 抢夺别人手牌的攻击性技能
                if (targetLocation != null) {
                    analyzeAggressiveSkillUsage(playerLocation, targetLocation, probs, 0.12)
                }
            }

            SkillId.JI_SONG -> {
                // 急送：鬼脚技能 - 移动场上的情报牌
                // 需要根据情报颜色和移动目标来判断身份倾向
                // 例如：将黑色情报从A移到B，暗示A是队友，B是敌人
                if (targetLocation != null) {
                    // 由于急送技能涉及情报移动，我们需要基于推测的关系来分析意图
                    // 如果没有具体的情报颜色和原位置，我们根据目标关系做推断

                    val userInferredIdentity = getInferredIdentity(playerLocation)
                    val targetInferredIdentity = getInferredIdentity(targetLocation)

                    // 如果对推测的队友使用急送，可能是在帮助队友
                    if (userInferredIdentity == targetInferredIdentity) {
                        analyzeCooperativeSkillUsage(playerLocation, targetLocation, probs, 0.08)
                    } else {
                        // 如果对推测的敌人使用急送，可能是在攻击
                        analyzeAggressiveSkillUsage(playerLocation, targetLocation, probs, 0.08)
                    }

                    // 急送技能的使用也表明了一定的策略考虑
                    analyzeInformationSkillUsage(playerLocation, targetLocation, probs, 0.05)
                }
            }

            SkillId.ZHUAN_JIAO -> {
                // 转交：白小年技能 - 传递手牌
                if (targetLocation != null) {
                    analyzeCooperativeSkillUsage(playerLocation, targetLocation, probs, 0.05)
                }
            }

            SkillId.QIANG_LING -> {
                // 强令：张一挺技能 - 对所有人的效果，无法用来判定身份
                // 该技能影响所有角色，不能从中推断身份倾向
                // 但使用强令可能表明用户认为当前局面对自己有利
                analyzeWinConditionSkillUsage(playerLocation, probs, 0.05)
            }

            SkillId.JIN_BI -> {
                // 禁闭：王田香技能 - 控制类技能
                if (targetLocation != null) {
                    analyzeAggressiveSkillUsage(playerLocation, targetLocation, probs, 0.1)
                }
            }

            SkillId.ZHI_YIN -> {
                // 知音：程小蝶技能 - 被动触发，无法控制，因此不能说明身份
                // 该技能是被动触发的，玩家无法控制，无法用于身份推断
            }

            SkillId.MIAO_SHOU -> {
                // 妙手：阿芙罗拉技能 - 偷取手牌
                if (targetLocation != null) {
                    analyzeAggressiveSkillUsage(playerLocation, targetLocation, probs, 0.1)
                }
            }

            SkillId.SOU_JI -> {
                // 搜缉：李醒技能 - 搜查手牌
                if (targetLocation != null) {
                    analyzeInformationSkillUsage(playerLocation, targetLocation, probs, 0.08)
                    analyzeAggressiveSkillUsage(playerLocation, targetLocation, probs, 0.1)
                }
            }

            else -> {
                // 对于其他技能，如果有目标则根据上下文分析
                if (targetLocation != null) {
                    analyzeContextualSkillUsage(playerLocation, targetLocation, probs, 0.02)
                }
            }
        }

        // Probabilities are already normalized by adjustProbability calls
    }

    /**
     * 分析合作性技能使用（如博爱、交际等）
     */
    private fun analyzeCooperativeSkillUsage(
        userLocation: Int,
        targetLocation: Int,
        probs: MutableMap<color, Double>,
        weight: Double
    ) {
        val targetInferredIdentity = getInferredIdentity(targetLocation)
        // 合作性技能使用暗示使用者和目标可能是队友
        when (targetInferredIdentity) {
            Red -> adjustProbability(probs, Red, weight)
            Blue -> adjustProbability(probs, Blue, weight)
            Black -> {
                // 对神秘人使用合作技能，可能使用者也是神秘人
                adjustProbability(probs, Black, weight * 0.5)
            }
            else -> {
                // 对失去身份或未知身份的玩家，无法做有效推断
            }
        }
    }

    /**
     * 分析保护性技能的使用模式
     */
    private fun analyzeProtectiveSkillUsage(
        userLocation: Int,
        targetLocation: Int?,
        probs: MutableMap<color, Double>,
        weight: Double
    ) {
        if (targetLocation == null) return

        val userInferredIdentity = getInferredIdentity(userLocation)
        val targetInferredIdentity = getInferredIdentity(targetLocation)

        // 如果对推测的队友使用保护技能，增强相同身份的概率
        if (userInferredIdentity == targetInferredIdentity) {
            adjustProbability(probs, userInferredIdentity, weight)
        }
        // 如果对推测的敌人使用保护技能，可能是伪装或误判
        else {
            adjustProbability(probs, targetInferredIdentity, weight * 0.3)
        }
    }

    /**
     * 分析攻击性技能的使用模式
     */
    private fun analyzeAggressiveSkillUsage(
        userLocation: Int,
        targetLocation: Int?,
        probs: MutableMap<color, Double>,
        weight: Double
    ) {
        if (targetLocation == null) return

        val userInferredIdentity = getInferredIdentity(userLocation)
        val targetInferredIdentity = getInferredIdentity(targetLocation)

        // 如果对推测的敌人使用攻击技能，增强相反身份的概率
        if (userInferredIdentity != targetInferredIdentity) {
            when (targetInferredIdentity) {
                Red -> adjustProbability(probs, Blue, weight)
                Blue -> adjustProbability(probs, Red, weight)
                Black -> {
                    // 攻击神秘人，红蓝都可能
                    adjustProbability(probs, Red, weight * 0.5)
                    adjustProbability(probs, Blue, weight * 0.5)
                }
                else -> { /* 未知身份 */ }
            }
        }
        // 如果对推测的队友使用攻击技能，可能是误判或伪装
        else {
            adjustProbability(probs, getOppositeIdentity(userInferredIdentity), weight * 0.2)
        }
    }

    /**
     * 分析信息类技能的使用模式
     */
    private fun analyzeInformationSkillUsage(
        userLocation: Int,
        targetLocation: Int?,
        probs: MutableMap<color, Double>,
        weight: Double
    ) {
        if (targetLocation == null) return

        // 信息技能通常用于获取或干扰情报，使用模式相对中性
        // 但频繁对某些玩家使用可能暗示关系
        val targetInferredIdentity = getInferredIdentity(targetLocation)

        // 信息技能的使用更多反映策略而非直接的身份暗示
        analyzeContextualSkillUsage(userLocation, targetLocation, probs, weight * 0.5)
    }

    /**
     * 分析上下文相关的技能使用
     */
    private fun analyzeContextualSkillUsage(
        userLocation: Int,
        targetLocation: Int?,
        probs: MutableMap<color, Double>,
        weight: Double
    ) {
        if (targetLocation == null) return

        // 基于当前推测的身份关系来分析技能使用的合理性
        val userInferredIdentity = getInferredIdentity(userLocation)
        val targetInferredIdentity = getInferredIdentity(targetLocation)

        // 如果技能使用符合预期的队友关系，略微增强概率
        if (userInferredIdentity == targetInferredIdentity) {
            adjustProbability(probs, userInferredIdentity, weight * 0.5)
        }
    }

    /**
     * 分析胜利条件相关技能的使用
     */
    private fun analyzeWinConditionSkillUsage(userLocation: Int, probs: MutableMap<color, Double>, weight: Double) {
        // 使用胜利条件技能通常表示玩家认为自己接近胜利
        // 这可以提供关于其身份的重要线索

        // 检查当前局面哪个阵营更有优势
        val redAdvantage = calculateRedAdvantage()
        val blueAdvantage = calculateBlueAdvantage()

        if (redAdvantage > blueAdvantage) {
            adjustProbability(probs, Red, weight)
        } else if (blueAdvantage > redAdvantage) {
            adjustProbability(probs, Blue, weight)
        }
        // 如果形势不明朗，可能是神秘人
        else {
            adjustProbability(probs, Black, weight * 0.3)
        }
    }

    /**
     * 计算红方优势程度
     */
    private fun calculateRedAdvantage(): Double {
        // 简化的优势计算，基于场上情报分布
        var redCount = 0.0
        var totalCount = 0.0

        game.players.filterNotNull().filter { it.alive }.forEach { player ->
            player.messageCards.forEach { card ->
                totalCount++
                if (card.colors.contains(Red)) {
                    redCount++
                }
            }
        }

        return if (totalCount > 0) redCount / totalCount else 0.5
    }

    /**
     * 计算蓝方优势程度
     */
    private fun calculateBlueAdvantage(): Double {
        // 简化的优势计算，基于场上情报分布
        var blueCount = 0.0
        var totalCount = 0.0

        game.players.filterNotNull().filter { it.alive }.forEach { player ->
            player.messageCards.forEach { card ->
                totalCount++
                if (card.colors.contains(Blue)) {
                    blueCount++
                }
            }
        }

        return if (totalCount > 0) blueCount / totalCount else 0.5
    }

    /**
     * 获取相反的身份
     */
    private fun getOppositeIdentity(identity: color): color {
        return when (identity) {
            Red -> Blue
            Blue -> Red
            else -> identity // 神秘人没有直接相反的身份
        }
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
