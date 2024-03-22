package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.card.count
import com.fengsheng.card.filter
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.color
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
        g.resolve(executeDuiZhengXiaYaoA(fsm, r))
    }

    private data class executeDuiZhengXiaYaoA(val fsm: FightPhaseIdle, val r: Player) : WaitingFsm {
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
                    val cache = hashMapOf<color, ArrayList<Card>>(
                        color.Black to ArrayList(),
                        color.Red to ArrayList(),
                        color.Blue to ArrayList()
                    )
                    for (card in r.cards) {
                        for (color in card.colors) cache[color]!!.add(card)
                    }
                    for ((key, cards) in cache) {
                        if (cards.size >= 2 && findColorMessageCard(g, listOf(key)) != null) {
                            g.tryContinueResolveProtocol(r, skillDuiZhengXiaYaoBTos {
                                enable = true
                                cards.take(2).forEach { cardIds.add(it.id) }
                            })
                            return@post
                        }
                    }
                    g.tryContinueResolveProtocol(r, skillDuiZhengXiaYaoBTos { })
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
            val playerAndCard = findColorMessageCard(g, colors)
            if (playerAndCard == null) {
                logger.error("场上没有选择的颜色的情报牌")
                player.sendErrorMessage("场上没有选择的颜色的情报牌")
                return null
            }
            r.incrSeq()
            return ResolveResult(executeDuiZhengXiaYaoB(fsm, r, cards, colors, playerAndCard), true)
        }
    }

    private data class executeDuiZhengXiaYaoB(
        val fsm: FightPhaseIdle,
        val r: Player,
        val cards: List<Card>,
        val colors: List<color>,
        val defaultSelection: PlayerAndCard
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            logger.info("${r}展示了${cards.joinToString()}")
            val g = r.game!!
            g.players.send { p ->
                skillDuiZhengXiaYaoBToc {
                    playerId = p.getAlternativeLocation(r.location)
                    enable = true
                    this@executeDuiZhengXiaYaoB.cards.forEach { cards.add(it.toPbCard()) }
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
                    g.tryContinueResolveProtocol(r, skillDuiZhengXiaYaoCTos {
                        targetPlayerId = r.getAlternativeLocation(defaultSelection.player.location)
                        messageCardId = defaultSelection.card.id
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
        private fun getSameColors(card1: Card, card2: Card): List<color> {
            val colors = ArrayList<color>()
            for (color in listOf(color.Black, color.Red, color.Blue)) {
                if (card1.colors.contains(color) && card2.colors.contains(color)) colors.add(color)
            }
            return colors
        }

        private fun findColorMessageCard(game: Game, colors: List<color?>): PlayerAndCard? {
            for (player in game.players) {
                for (card in player!!.messageCards) {
                    for (color in card.colors) {
                        if (colors.contains(color)) return PlayerAndCard(player, card)
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
            !g.players.any {
                it!!.isPartnerOrSelf(player) && it.willWin(
                    e.whoseTurn,
                    e.inFrontOfWhom,
                    e.messageCard
                )
            } || return false
            g.players.any {
                it!!.isEnemy(player) && it.willWin(
                    e.whoseTurn,
                    e.inFrontOfWhom,
                    e.messageCard
                )
            } || target.isPartnerOrSelf(player) && target.willDie(e.messageCard) || return false
            var cards =
                e.messageCard.colors.filter { it != color.Black && target.messageCards.count(it) == 2 }
                    .flatMap { color ->
                        target.messageCards.filter { color in it.colors }
                            .run { filter { !it.isBlack() }.ifEmpty { this } }
                    }
            if (cards.isEmpty() && e.messageCard.isBlack() && target.messageCards.count(color.Black) == 2) {
                cards = target.messageCards.filter(color.Black).run {
                    filter { it.isPureBlack() }.ifEmpty { this }
                }
            }
            var card =
                cards.run { filter { player.identity !in it.colors }.ifEmpty { this } }
                    .randomOrNull() ?: return false
            player.game!!.players.anyoneWillWinOrDie(e) || return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skillDuiZhengXiaYaoATos { })
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}
