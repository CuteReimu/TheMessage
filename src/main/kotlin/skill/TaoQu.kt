package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.card.count
import com.fengsheng.card.filter
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Role.skill_tao_qu_a_tos
import com.fengsheng.protos.Role.skill_tao_qu_b_tos
import com.fengsheng.protos.skillTaoQuAToc
import com.fengsheng.protos.skillTaoQuATos
import com.fengsheng.protos.skillTaoQuBToc
import com.fengsheng.protos.skillTaoQuBTos
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * SP白菲菲技能【套取】：出牌阶段限一次，你可以展示两张含含相同颜色的牌，将一名其他角色情报区的一张同色情报加入手牌。
 */
class TaoQu : MainPhaseSkill() {
    override val skillId = SkillId.TAO_QU

    override val isInitialSkill = true

    override fun mainPhaseNeedNotify(r: Player): Boolean = super.mainPhaseNeedNotify(r) && listOf(Black, Red, Blue).any {
        r.cards.count(it) >= 2 && r.game!!.players.any { p ->
            p !== r && p!!.alive && p.messageCards.any { c -> it in c.colors }
        }
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? MainPhaseIdle
        if (r !== fsm?.whoseTurn) {
            logger.error("现在不是出牌阶段空闲时点")
            r.sendErrorMessage("现在不是出牌阶段空闲时点")
            return
        }
        if (r.getSkillUseCount(skillId) > 0) {
            logger.error("[套取]一回合只能发动一次")
            r.sendErrorMessage("[套取]一回合只能发动一次")
            return
        }
        val pb = message as skill_tao_qu_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        if (pb.cardIdsCount != 2) {
            logger.error("你必须选择两张手牌")
            r.sendErrorMessage("你必须选择两张手牌")
            return
        }
        val cards = pb.cardIdsList.map {
            val card = r.findCard(it)
            if (card == null) {
                logger.error("没有这张牌")
                r.sendErrorMessage("没有这张牌")
                return
            }
            card
        }
        val colors = cards[0].colors.filter { it in cards[1].colors }
        if (colors.isEmpty()) {
            logger.error("你选择的两张牌不含相同颜色")
            r.sendErrorMessage("你选择的两张牌不含相同颜色")
            return
        }
        if (!colors.any { c ->
                g.players.any {
                    it !== r && it!!.alive && it.messageCards.any { card -> c in card.colors }
                }
            }) {
            logger.error("除自己以外场上没有你选择的颜色的情报")
            r.sendErrorMessage("除自己以外场上没有你选择的颜色的情报")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        logger.info("${r}发动了[套取]，展示了${cards.joinToString()}")
        cards.forEach { c ->
            g.players.forEach { it!!.canWeiBiCardIds.add(c.id) }
        }
        g.resolve(ExecuteTaoQu(fsm, r, cards, colors))
    }

    private class ExecuteTaoQu(
        val fsm: MainPhaseIdle,
        val r: Player,
        val cards: List<Card>,
        val colors: List<color>
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            g.players.send { p ->
                skillTaoQuAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    colors.addAll(this@ExecuteTaoQu.colors)
                    this@ExecuteTaoQu.cards.forEach { cards.add(it.toPbCard()) }
                    waitingSecond = g.waitSecond
                    if (p === r) {
                        val seq = p.seq
                        this.seq = seq
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq)) {
                                val playerAndCard = g.players.flatMap {
                                    if (it === p || !it!!.alive) emptyList()
                                    else it.messageCards.mapNotNull { card ->
                                        if (!card.colors.any { c -> c in colors }) null
                                        else PlayerAndCard(it, card)
                                    }
                                }.random()
                                g.tryContinueResolveProtocol(r, skillTaoQuBTos {
                                    targetPlayerId = p.getAlternativeLocation(playerAndCard.player.location)
                                    cardId = playerAndCard.card.id
                                    this.seq = seq
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val color = listOf(Red, Blue, Black).filter {
                        cards.count(it) >= 2
                    } // 展示的两张牌都含有的颜色
                    val players =
                        g.players.filter { it!!.alive && it !== r && it.messageCards.isNotEmpty() } // 过滤出除了自己且存活有情报的玩家
                    val moveplayerAndcards = ArrayList<PlayerAndCard>() // 存储可能指定的玩家以及情报牌的集合
                    var value = Int.MIN_VALUE

                    // 通用的卡牌价值计算逻辑
                    fun calculatePlayerCardValues() {
                        for (p in players) {
                            for (movecard in p!!.messageCards.filter { c -> c.colors.any { it in color } }) {
                                val v = r.calculateRemoveCardValue(r, p, movecard)
                                if (v > value) {
                                    value = v
                                    moveplayerAndcards.clear()
                                    moveplayerAndcards.add(PlayerAndCard(p, movecard))
                                } else if (v == value) {
                                    moveplayerAndcards.add(PlayerAndCard(p, movecard))
                                }
                            }
                        }
                    }

                    // 特殊处理神秘人的情况，优先平衡场上局势
                    if (r.identity == Black) {
                        // 找出所有可能的目标玩家和卡牌
                        val allPossibleTargets = ArrayList<PlayerAndCard>()
                        for (p in players) {
                            for (movecard in p!!.messageCards.filter { c -> c.colors.any { it in color } }) {
                                allPossibleTargets.add(PlayerAndCard(p, movecard))
                            }
                        }

                        // 优先级1: 阻止即将获胜的玩家（拥有2张同色真实情报的玩家）
                        val redWinTargets = allPossibleTargets.filter { playerAndCard ->
                            playerAndCard.player.identity == Red && Red in playerAndCard.card.colors &&
                                playerAndCard.player.messageCards.count(Red) >= 2
                        }
                        val blueWinTargets = allPossibleTargets.filter { playerAndCard ->
                            playerAndCard.player.identity == Blue && Blue in playerAndCard.card.colors &&
                                playerAndCard.player.messageCards.count(Blue) >= 2
                        }

                        when {
                            redWinTargets.isNotEmpty() && blueWinTargets.isNotEmpty() -> {
                                // 如果红蓝双方都有人快要赢了，优先阻止拥有更多同色情报的一方
                                val maxRedCards = redWinTargets.maxOf { it.player.messageCards.count(Red) }
                                val maxBlueCards = blueWinTargets.maxOf { it.player.messageCards.count(Blue) }
                                moveplayerAndcards.addAll(
                                    when {
                                        maxRedCards > maxBlueCards -> redWinTargets
                                        maxBlueCards > maxRedCards -> blueWinTargets
                                        else -> if (Random.nextBoolean()) redWinTargets else blueWinTargets
                                    }
                                )
                            }
                            redWinTargets.isNotEmpty() -> moveplayerAndcards.addAll(redWinTargets)
                            blueWinTargets.isNotEmpty() -> moveplayerAndcards.addAll(blueWinTargets)
                            else -> {
                                // 优先级2: 平衡红蓝双方，优先移除强势一方的情报
                                val redPlayers = g.players.filter { it!!.alive && it.identity == Red }
                                val bluePlayers = g.players.filter { it!!.alive && it.identity == Blue }

                                // 计算红蓝双方的总体实力（拥有的同色情报数量）
                                val redStrength = redPlayers.sumOf { it!!.messageCards.count(Red) }
                                val blueStrength = bluePlayers.sumOf { it!!.messageCards.count(Blue) }

                                // 优先移除强势一方的情报来平衡局势
                                val targetFaction = if (redStrength > blueStrength) {
                                    Red
                                } else if (blueStrength > redStrength) {
                                    Blue
                                } else {
                                    null
                                }

                                val balanceTargets = if (targetFaction != null) {
                                    allPossibleTargets.filter { playerAndCard ->
                                        playerAndCard.player.identity == targetFaction &&
                                            targetFaction in playerAndCard.card.colors
                                    }
                                } else {
                                    // 如果双方平衡，则选择拥有更多同色情报的玩家
                                    allPossibleTargets.filter { playerAndCard ->
                                        val playerIdentity = playerAndCard.player.identity
                                        playerIdentity != Black && playerIdentity in playerAndCard.card.colors &&
                                            playerAndCard.player.messageCards.count(playerIdentity) >= 1
                                    }
                                }

                                if (balanceTargets.isNotEmpty()) {
                                    moveplayerAndcards.addAll(balanceTargets)
                                } else {
                                    // 优先级3: 如果没有明显的平衡目标，使用原始逻辑
                                    calculatePlayerCardValues()
                                }
                            }
                        }
                    } else {
                        // 非神秘人使用原始逻辑
                        calculatePlayerCardValues()
                    }

                    val bestcard = moveplayerAndcards.map { it.card }.bestCard(r.identity)
                    val playerAndCard = moveplayerAndcards.first { it.card === bestcard } // 目标玩家和情报牌
                    g.tryContinueResolveProtocol(r, skillTaoQuBTos {
                        targetPlayerId = r.getAlternativeLocation(playerAndCard.player.location)
                        cardId = playerAndCard.card.id
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== fsm.whoseTurn) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_tao_qu_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = player.game!!
            if (player is HumanPlayer && !player.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${player.seq}, actual Seq: ${message.seq}")
                player.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            if (message.targetPlayerId == 0) {
                logger.error("不能以自己为目标")
                player.sendErrorMessage("不能以自己为目标")
                return null
            }
            val target = g.players[player.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.cardId)
            if (card == null) {
                logger.error("没有这张情报")
                player.sendErrorMessage("没有这张情报")
                return null
            }
            if (!card.colors.any { it in colors }) {
                logger.error("选择的情报没有该颜色")
                player.sendErrorMessage("选择的情报没有该颜色")
                return null
            }
            player.incrSeq()
            logger.info("${player}将${target}面前的${card}加入了手牌")
            target.deleteMessageCard(card.id)
            player.cards.add(card)
            g.players.send {
                skillTaoQuBToc {
                    playerId = it.getAlternativeLocation(player.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    cardId = card.id
                }
            }
            return ResolveResult(fsm, true)
        }
    }

    companion object {
        fun ai(e: MainPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseTurn
            player.getSkillUseCount(SkillId.TAO_QU) == 0 || return false
            val players =
                player.game!!.players.filter { it!!.alive && it !== player && it.messageCards.isNotEmpty() }
            players.isNotEmpty() || return false
            val color = listOf(Red, Blue, Black).filter {
                player.cards.count(it) >= 2
            }
            color.isNotEmpty() || return false
            var value = -9
            var choosecolor = Black

            // 通用的价值计算逻辑
            fun calculateBestColor() {
                for (p in players) {
                    val messagecards = p!!.messageCards.toList()
                    for (card in messagecards) {
                        val c = card.colors.shuffled().find { it in color } ?: continue
                        val v = player.calculateRemoveCardValue(player, p, card)
                        if (v > value) {
                            value = v
                            choosecolor = c
                        }
                    }
                }
            }

            // 神秘人特殊处理：检查是否有需要平衡的情况
            if (player.identity == Black) {
                // 检查是否有玩家即将获胜（拥有2张同色真实情报）
                val redPlayersCloseToWin = players.filter { p ->
                    p!!.identity == Red && p.messageCards.count(Red) >= 2
                }
                val bluePlayersCloseToWin = players.filter { p ->
                    p!!.identity == Blue && p.messageCards.count(Blue) >= 2
                }

                // 检查红蓝双方是否失衡
                val redPlayers = player.game!!.players.filter { it!!.alive && it.identity == Red }
                val bluePlayers = player.game!!.players.filter { it!!.alive && it.identity == Blue }
                val redStrength = redPlayers.sumOf { it!!.messageCards.count(Red) }
                val blueStrength = bluePlayers.sumOf { it!!.messageCards.count(Blue) }
                val isImbalanced = kotlin.math.abs(redStrength - blueStrength) >= 2

                // 如果有玩家即将获胜或场上失衡，则积极使用技能
                if (redPlayersCloseToWin.isNotEmpty() || bluePlayersCloseToWin.isNotEmpty() || isImbalanced) {
                    // 根据策略选择颜色：优先选择能阻止获胜或平衡局势的颜色
                    when {
                        redPlayersCloseToWin.isNotEmpty() && bluePlayersCloseToWin.isNotEmpty() -> {
                            // 双方都有人快赢，选择拥有更多情报的一方对应的颜色
                            val maxRedCards = redPlayersCloseToWin.maxOf { it!!.messageCards.count(Red) }
                            val maxBlueCards = bluePlayersCloseToWin.maxOf { it!!.messageCards.count(Blue) }
                            choosecolor = when {
                                maxRedCards > maxBlueCards && Red in color -> Red
                                maxBlueCards > maxRedCards && Blue in color -> Blue
                                else -> {
                                    // 相等时随机选择
                                    val availableColors = listOfNotNull(
                                        if (Red in color) Red else null,
                                        if (Blue in color) Blue else null
                                    )
                                    if (availableColors.isNotEmpty()) availableColors.random() else color.first()
                                }
                            }
                        }
                        redPlayersCloseToWin.isNotEmpty() && Red in color -> choosecolor = Red
                        bluePlayersCloseToWin.isNotEmpty() && Blue in color -> choosecolor = Blue
                        redStrength > blueStrength && Red in color -> choosecolor = Red
                        blueStrength > redStrength && Blue in color -> choosecolor = Blue
                        redStrength == blueStrength -> {
                            // 实力相等时随机选择
                            val availableColors = listOfNotNull(
                                if (Red in color) Red else null,
                                if (Blue in color) Blue else null
                            )
                            choosecolor = if (availableColors.isNotEmpty()) availableColors.random() else color.first()
                        }
                        redStrength > blueStrength -> {
                            // 红方更强但红色不可用，选择可用颜色
                            choosecolor = if (Blue in color) Blue else color.first()
                        }
                        blueStrength > redStrength -> {
                            // 蓝方更强但蓝色不可用，选择可用颜色
                            choosecolor = if (Red in color) Red else color.first()
                        }
                    }
                    value = 10 // 设置一个正值以触发技能使用
                } else {
                    // 否则使用原始逻辑
                    calculateBestColor()
                }
            } else {
                // 非神秘人使用原始逻辑
                calculateBestColor()
            }

            value > -9 || return false // 如果没有找到合适的情报，则不发动
            val cardIds = player.cards.filter(choosecolor).shuffled().take(2).map { it.id }
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillTaoQuATos { this.cardIds.addAll(cardIds) })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
