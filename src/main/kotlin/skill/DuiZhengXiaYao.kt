package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.card.count
import com.fengsheng.card.filter
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

/**
 * 黄济仁技能【对症下药】：争夺阶段，你可以翻开此角色牌，然后摸三张牌，并且你可以展示两张含有相同颜色的手牌，然后从一名角色的情报区，弃置一张对应颜色情报。
 */
class DuiZhengXiaYao : ActiveSkill {
    override val skillId = SkillId.DUI_ZHENG_XIA_YAO

    override val isInitialSkill = true

    override fun canUse(fightPhase: FightPhaseIdle, r: Player): Boolean = !r.roleFaceUp

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessage) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            logger.error("现在不是发动[对症下药]的时机")
            r.sendErrorMessage("现在不是发动[对症下药]的时机")
            return
        }
        if (r.roleFaceUp) {
            logger.error("你现在正面朝上，不能发动[对症下药]")
            r.sendErrorMessage("你现在正面朝上，不能发动[对症下药]")
            return
        }
        val pb = message as skill_dui_zheng_xia_yao_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        logger.info("${r}发动了[对症下药]")
        r.draw(3)
        g.resolve(ExecuteDuiZhengXiaYaoA(fsm, r))
    }

    private data class ExecuteDuiZhengXiaYaoA(val fsm: FightPhaseIdle, val r: Player) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            val g = r.game!!
            g.players.send { p ->
                skillDuiZhengXiaYaoAToc {
                    playerId = p.getAlternativeLocation(r.location)
                    waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq2 = p.seq
                        seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, skillDuiZhengXiaYaoBTos {
                                    enable = false
                                    seq = seq2
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val messageCards = fsm.inFrontOfWhom.messageCards.toList()
                    fsm.inFrontOfWhom.messageCards.add(fsm.messageCard) // 先假设这个人已经获得了这张情报
                    var chooseColor = Black
                    var value = -1
                    for (c in listOf(Red, Blue, Black)) { // 对红、蓝、黑三种颜色进行判断
                        r.cards.count(c) >= 2 || continue // 如果这个颜色不够2张，则跳过
                        for (card in messageCards) { // 遍历目标角色面前的所有情报
                            c in card.colors || continue // 如果这张情报不含有这个颜色，则跳过
                            val v = r.calculateRemoveCardValue(fsm.whoseTurn, fsm.inFrontOfWhom, card) // 计算弃掉这张情报的价值
                            if (v > value) { // 如果这张情报的价值更高，则选这张
                                value = v
                                chooseColor = c
                            }
                        }
                    }
                    fsm.inFrontOfWhom.messageCards.removeLast() // 把刚才加上的那张情报移除
                    if (value < 0) { // 如果没有找到合适的情报，则不展示两张牌
                        g.tryContinueResolveProtocol(r, skillDuiZhengXiaYaoBTos { })
                        return@post
                    }
                    val cards = r.cards.filter(chooseColor).shuffled().take(2) // 随机选这个颜色的两张牌
                    g.tryContinueResolveProtocol(r, skillDuiZhengXiaYaoBTos {
                        enable = true
                        cards.forEach { cardIds.add(it.id) }
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_dui_zheng_xia_yao_b_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                g.players.send {
                    skillDuiZhengXiaYaoBToc {
                        playerId = it.getAlternativeLocation(r.location)
                        enable = false
                    }
                }
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            if (message.cardIdsCount != 2) {
                logger.error("enable为true时必须要发两张牌")
                player.sendErrorMessage("不足两张牌")
                return null
            }
            val cards = List(2) { i ->
                val card = r.findCard(message.getCardIds(i))
                if (card == null) {
                    logger.error("没有这张卡")
                    player.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            val colors = getSameColors(cards[0], cards[1])
            if (colors.isEmpty()) {
                logger.error("两张牌没有相同的颜色")
                player.sendErrorMessage("两张牌没有相同的颜色")
                return null
            }
            val playerAndCard = r.findColorMessageCard(colors)
            if (playerAndCard == null) {
                logger.error("场上没有选择的颜色的情报牌")
                player.sendErrorMessage("场上没有选择的颜色的情报牌")
                return null
            }
            r.incrSeq()
            return ResolveResult(ExecuteDuiZhengXiaYaoB(fsm, r, cards, colors, playerAndCard), true)
        }
    }

    private data class ExecuteDuiZhengXiaYaoB(
        val fsm: FightPhaseIdle,
        val r: Player,
        val cards: List<Card>,
        val colors: List<color>,
        val defaultSelection: PlayerAndCard
    ) : WaitingFsm {
        override val whoseTurn: Player
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            logger.info("${r}展示了${cards.joinToString()}")
            val g = r.game!!
            g.players.send { p ->
                skillDuiZhengXiaYaoBToc {
                    playerId = p.getAlternativeLocation(r.location)
                    enable = true
                    this@ExecuteDuiZhengXiaYaoB.cards.forEach { cards.add(it.toPbCard()) }
                    waitingSecond = Config.WaitSecond
                    if (p === r) {
                        val seq2 = p.seq
                        seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(r, skillDuiZhengXiaYaoCTos {
                                    targetPlayerId = r.getAlternativeLocation(defaultSelection.player.location)
                                    messageCardId = defaultSelection.card.id
                                    seq = seq2
                                })
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    fsm.inFrontOfWhom.messageCards.add(fsm.messageCard) // 先假设这个人已经获得了这张情报
                    var playerAndCard: PlayerAndCard? = null
                    var value = Int.MIN_VALUE
                    for (p in g.players) {
                        p!!.alive || continue
                        for (card in p.messageCards.sortedBy { it.isBlack() }) { // 遍历所有玩家面前的所有情报
                            // 先遍历不带黑颜色的情报，这样就不会出现优先弃掉对方的真黑双色情报的问题了
                            card !== fsm.messageCard || continue // 如果遍历到了前面假设获得的那张情报，则跳过
                            card.colors.any { it in colors } || continue // 如果这张情报不含有这个颜色，则跳过
                            val v = r.calculateRemoveCardValue(fsm.whoseTurn, p, card) // 计算弃掉这张情报的价值
                            if (v > value) { // 如果这张情报的价值更高，则选这张
                                value = v
                                playerAndCard = PlayerAndCard(p, card)
                            }
                        }
                    }
                    fsm.inFrontOfWhom.messageCards.removeLast() // 把刚才加上的那张情报移除
                    playerAndCard!! // 从逻辑上来说，必定能找到一个合适的情报，否则上一条协议会拦截
                    g.tryContinueResolveProtocol(r, skillDuiZhengXiaYaoCTos {
                        targetPlayerId = r.getAlternativeLocation(playerAndCard.player.location)
                        messageCardId = playerAndCard.card.id
                    })
                }, 3, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (player !== r) {
                logger.error("不是你发技能的时机")
                player.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_dui_zheng_xia_yao_c_tos) {
                logger.error("错误的协议")
                player.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                logger.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                logger.error("目标错误")
                player.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                logger.error("目标已死亡")
                player.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.messageCardId)
            if (card == null) {
                logger.error("没有这张牌")
                player.sendErrorMessage("没有这张牌")
                return null
            }
            var contains = false
            for (color in colors) {
                if (card.colors.contains(color)) {
                    contains = true
                    break
                }
            }
            if (!contains) {
                logger.error("选择的情报不含有指定的颜色")
                player.sendErrorMessage("选择的情报不含有指定的颜色")
                return null
            }
            r.incrSeq()
            logger.info("${r}弃掉了${target}面前的$card")
            g.deck.discard(target.deleteMessageCard(card.id)!!)
            g.players.send {
                skillDuiZhengXiaYaoCToc {
                    playerId = it.getAlternativeLocation(r.location)
                    targetPlayerId = it.getAlternativeLocation(target.location)
                    messageCardId = card.id
                }
            }
            return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
        }
    }

    companion object {
        private fun getSameColors(card1: Card, card2: Card) =
            listOf(Black, Red, Blue).filter { it in card1.colors && it in card2.colors }

        private fun Player.findColorMessageCard(colors: List<color?>): PlayerAndCard? {
            for (player in game!!.sortedFrom(game!!.players, location)) {
                for (card in player.messageCards) {
                    for (color in card.colors) {
                        if (color in colors) return PlayerAndCard(player, card)
                    }
                }
            }
            return null
        }

        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            val target = e.inFrontOfWhom
            val g = player.game!!
            !player.roleFaceUp || return false
            if (g.players.any {
                    it!!.isPartnerOrSelf(player) && it.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
                }) return false
            g.players.any {
                it!!.isEnemy(player) && it.willWin(e.whoseTurn, e.inFrontOfWhom, e.messageCard)
            } || target.isPartnerOrSelf(player) && target.willDie(e.messageCard) || return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillDuiZhengXiaYaoATos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
