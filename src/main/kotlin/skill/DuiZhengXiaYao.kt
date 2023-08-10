package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.Card
import com.fengsheng.card.PlayerAndCard
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Role.*
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * 黄济仁技能【对症下药】：争夺阶段，你可以翻开此角色牌，然后摸三张牌，并且你可以展示两张含有相同颜色的手牌，然后从一名角色的情报区，弃置一张对应颜色情报。
 */
class DuiZhengXiaYao : AbstractSkill(), ActiveSkill {
    override val skillId = SkillId.DUI_ZHENG_XIA_YAO

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        val fsm = g.fsm as? FightPhaseIdle
        if (r !== fsm?.whoseFightTurn) {
            log.error("现在不是发动[对症下药]的时机")
            (r as? HumanPlayer)?.sendErrorMessage("现在不是发动[对症下药]的时机")
            return
        }
        if (r.roleFaceUp) {
            log.error("你现在正面朝上，不能发动[对症下药]")
            (r as? HumanPlayer)?.sendErrorMessage("你现在正面朝上，不能发动[对症下药]")
            return
        }
        val pb = message as skill_dui_zheng_xia_yao_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${pb.seq}")
            r.sendErrorMessage("操作太晚了")
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        log.info("${r}发动了[对症下药]")
        r.draw(3)
        g.resolve(executeDuiZhengXiaYaoA(fsm, r))
    }

    private data class executeDuiZhengXiaYaoA(val fsm: FightPhaseIdle, val r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_dui_zheng_xia_yao_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_dui_zheng_xia_yao_b_tos.newBuilder()
                                builder2.enable = false
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
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
                            val builder = skill_dui_zheng_xia_yao_b_tos.newBuilder()
                            builder.enable = true
                            for (card in cards) {
                                builder.addCardIds(card.id)
                                if (builder.cardIdsCount >= 2) break
                            }
                            g.tryContinueResolveProtocol(r, builder.build())
                            return@post
                        }
                    }
                    g.tryContinueResolveProtocol(r, skill_dui_zheng_xia_yao_b_tos.newBuilder().setEnable(false).build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_dui_zheng_xia_yao_b_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        val builder = skill_dui_zheng_xia_yao_b_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.enable = false
                        p.send(builder.build())
                    }
                }
                return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
            }
            if (message.cardIdsCount != 2) {
                log.error("enable为true时必须要发两张牌")
                (player as? HumanPlayer)?.sendErrorMessage("不足两张牌")
                return null
            }
            val cards = Array(2) { i ->
                val card = r.findCard(message.getCardIds(i))
                if (card == null) {
                    log.error("没有这张卡")
                    (player as? HumanPlayer)?.sendErrorMessage("没有这张卡")
                    return null
                }
                card
            }
            val colors = getSameColors(cards[0], cards[1])
            if (colors.isEmpty()) {
                log.error("两张牌没有相同的颜色")
                (player as? HumanPlayer)?.sendErrorMessage("两张牌没有相同的颜色")
                return null
            }
            val playerAndCard = findColorMessageCard(g, colors)
            if (playerAndCard == null) {
                log.error("场上没有选择的颜色的情报牌")
                (player as? HumanPlayer)?.sendErrorMessage("场上没有选择的颜色的情报牌")
                return null
            }
            r.incrSeq()
            return ResolveResult(executeDuiZhengXiaYaoB(fsm, r, cards, colors, playerAndCard), true)
        }

        companion object {
            private val log = Logger.getLogger(executeDuiZhengXiaYaoA::class.java)
        }
    }

    private data class executeDuiZhengXiaYaoB(
        val fsm: FightPhaseIdle,
        val r: Player,
        val cards: Array<Card>,
        val colors: List<color>,
        val defaultSelection: PlayerAndCard
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            log.info("${r}展示了${cards.contentToString()}")
            val g = r.game!!
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_dui_zheng_xia_yao_b_toc.newBuilder().setEnable(true)
                    for (card in cards) builder.addCards(card.toPbCard())
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.timeout = GameExecutor.post(g, {
                            if (p.checkSeq(seq2)) {
                                val builder2 = skill_dui_zheng_xia_yao_c_tos.newBuilder()
                                builder2.targetPlayerId = r.getAlternativeLocation(defaultSelection.player.location)
                                builder2.messageCardId = defaultSelection.card.id
                                builder2.seq = seq2
                                g.tryContinueResolveProtocol(r, builder2.build())
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(g, {
                    val builder = skill_dui_zheng_xia_yao_c_tos.newBuilder()
                    builder.targetPlayerId = r.getAlternativeLocation(defaultSelection.player.location)
                    builder.messageCardId = defaultSelection.card.id
                    g.tryContinueResolveProtocol(r, builder.build())
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                (player as? HumanPlayer)?.sendErrorMessage("不是你发技能的时机")
                return null
            }
            if (message !is skill_dui_zheng_xia_yao_c_tos) {
                log.error("错误的协议")
                (player as? HumanPlayer)?.sendErrorMessage("错误的协议")
                return null
            }
            val g = r.game!!
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: ${r.seq}, actual Seq: ${message.seq}")
                r.sendErrorMessage("操作太晚了")
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                (player as? HumanPlayer)?.sendErrorMessage("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]!!
            if (!target.alive) {
                log.error("目标已死亡")
                (player as? HumanPlayer)?.sendErrorMessage("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.messageCardId)
            if (card == null) {
                log.error("没有这张牌")
                (player as? HumanPlayer)?.sendErrorMessage("没有这张牌")
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
                log.error("选择的情报不含有指定的颜色")
                (player as? HumanPlayer)?.sendErrorMessage("选择的情报不含有指定的颜色")
                return null
            }
            r.incrSeq()
            log.info("${r}弃掉了${target}面前的${card}")
            g.deck.discard(target.deleteMessageCard(card.id)!!)
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_dui_zheng_xia_yao_c_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.messageCardId = card.id
                    p.send(builder.build())
                }
            }
            return ResolveResult(fsm.copy(whoseFightTurn = fsm.inFrontOfWhom), true)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as executeDuiZhengXiaYaoB

            if (fsm != other.fsm) return false
            if (r != other.r) return false
            if (!cards.contentEquals(other.cards)) return false
            if (colors != other.colors) return false
            if (defaultSelection != other.defaultSelection) return false

            return true
        }

        override fun hashCode(): Int {
            var result = fsm.hashCode()
            result = 31 * result + r.hashCode()
            result = 31 * result + cards.contentHashCode()
            result = 31 * result + colors.hashCode()
            result = 31 * result + defaultSelection.hashCode()
            return result
        }

        companion object {
            private val log = Logger.getLogger(executeDuiZhengXiaYaoB::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(DuiZhengXiaYao::class.java)
        private fun getSameColors(card1: Card, card2: Card): List<color> {
            val colors = ArrayList<color>()
            for (color in arrayOf(color.Black, color.Red, color.Blue)) {
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
            if (player.roleFaceUp) return false
            val playerCount = player.game!!.players.size
            val playerAndCards = player.game!!.players.filter { it!!.alive }.flatMap { p ->
                p!!.messageCards.map { PlayerAndCard(p, it) }
            }
            if (playerAndCards.size < playerCount) return false
            if (Random.nextInt(playerCount * playerCount) != 0) return false
            GameExecutor.post(player.game!!, {
                skill.executeProtocol(player.game!!, player, skill_dui_zheng_xia_yao_a_tos.getDefaultInstance())
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}