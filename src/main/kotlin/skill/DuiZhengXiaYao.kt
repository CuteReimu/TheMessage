package com.fengsheng.skill

import com.fengsheng.*
import com.fengsheng.card.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.protos.Common.card
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Errcode
import com.fengsheng.protos.Errcode.error_code_toc
import com.fengsheng.protos.Role
import com.fengsheng.protos.Role.*
import com.fengsheng.skill.DuiZhengXiaYao.executeDuiZhengXiaYaoA
import com.fengsheng.skill.DuiZhengXiaYao.executeDuiZhengXiaYaoB
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.*
import java.util.concurrent.*

/**
 * 黄济仁技能【对症下药】：争夺阶段，你可以翻开此角色牌，然后摸三张牌，并且你可以展示两张含有相同颜色的手牌，然后从一名角色的情报区，弃置一张对应颜色情报。
 */
class DuiZhengXiaYao : AbstractSkill(), ActiveSkill {
    override fun getSkillId(): SkillId? {
        return SkillId.DUI_ZHENG_XIA_YAO
    }

    override fun executeProtocol(g: Game, r: Player, message: GeneratedMessageV3) {
        if (g.fsm !is FightPhaseIdle || r !== fsm.whoseFightTurn) {
            log.error("现在不是发动[对症下药]的时机")
            return
        }
        if (r.isRoleFaceUp) {
            log.error("你现在正面朝上，不能发动[对症下药]")
            return
        }
        val pb = message as Role.skill_dui_zheng_xia_yao_a_tos
        if (r is HumanPlayer && !r.checkSeq(pb.seq)) {
            log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + pb.seq)
            return
        }
        r.incrSeq()
        r.addSkillUseCount(skillId)
        g.playerSetRoleFaceUp(r, true)
        log.info(r.toString() + "发动了[对症下药]")
        r.draw(3)
        g.resolve(executeDuiZhengXiaYaoA(fsm, r))
    }

    private class executeDuiZhengXiaYaoA(fsm: FightPhaseIdle, r: Player) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            val g = r.game
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_dui_zheng_xia_yao_a_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) {
                                g.tryContinueResolveProtocol(
                                    r, Role.skill_dui_zheng_xia_yao_b_tos.newBuilder()
                                        .setEnable(false).setSeq(seq2).build()
                                )
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    val cache = EnumMap<color?, MutableList<Card>>(
                        color::class.java
                    )
                    cache[color.Black] = ArrayList()
                    cache[color.Red] = ArrayList()
                    cache[color.Blue] = ArrayList()
                    for (card in r.getCards().values) {
                        for (color in card.colors) cache[color]!!.add(card)
                    }
                    for ((key, cards) in cache) {
                        if (cards.size >= 2 && findColorMessageCard(
                                g, java.util.List.of(
                                    key
                                )
                            ) != null
                        ) {
                            val builder = Role.skill_dui_zheng_xia_yao_b_tos.newBuilder().setEnable(true)
                            for (card in cards) {
                                builder.addCardIds(card.id)
                                if (builder.cardIdsCount >= 2) break
                            }
                            g.tryContinueResolveProtocol(r, builder.build())
                            return@post
                        }
                    }
                    g.tryContinueResolveProtocol(
                        r,
                        Role.skill_dui_zheng_xia_yao_b_tos.newBuilder().setEnable(false).build()
                    )
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_dui_zheng_xia_yao_b_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (!message.enable) {
                r.incrSeq()
                for (p in g.players) {
                    (p as? HumanPlayer)?.send(
                        skill_dui_zheng_xia_yao_b_toc.newBuilder().setPlayerId(p.getAlternativeLocation(r.location()))
                            .setEnable(false).build()
                    )
                }
                fsm.whoseFightTurn = fsm.inFrontOfWhom
                return ResolveResult(fsm, true)
            }
            if (message.cardIdsCount != 2) {
                log.error("enable为true时必须要发两张牌")
                return null
            }
            val cards = arrayOfNulls<Card>(2)
            for (i in cards.indices) {
                val card = r.findCard(message.getCardIds(i))
                if (card == null) {
                    log.error("没有这张卡")
                    return null
                }
                cards[i] = card
            }
            val colors = getSameColors(cards[0], cards[1])
            if (colors.isEmpty()) {
                log.error("两张牌没有相同的颜色")
                return null
            }
            val playerAndCard = findColorMessageCard(g, colors)
            if (playerAndCard == null) {
                log.error("场上没有选择的颜色的情报牌")
                (player as? HumanPlayer)?.send(
                    error_code_toc.newBuilder().setCode(Errcode.error_code.no_color_message_card).build()
                )
                return null
            }
            r.incrSeq()
            return ResolveResult(executeDuiZhengXiaYaoB(fsm, r, cards, colors, playerAndCard), true)
        }

        val fsm: FightPhaseIdle
        val r: Player

        init {
            this.card = card
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            this.card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            this.card = card
            this.player = player
            this.card = card
            this.card = card
            this.drawCards = drawCards
            this.players = players
            this.mainPhaseIdle = mainPhaseIdle
            this.dieSkill = dieSkill
            this.player = player
            this.player = player
            this.onUseCard = onUseCard
            this.game = game
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.dir = dir
            this.targetPlayer = targetPlayer
            this.lockedPlayers = lockedPlayers
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.player = player
            this.whoseTurn = whoseTurn
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.fightPhase = fightPhase
            this.player = player
            this.sendPhase = sendPhase
            this.dieGiveCard = dieGiveCard
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.isMessageCardFaceUp = isMessageCardFaceUp
            this.waitForChengQing = waitForChengQing
            this.waitForChengQing = waitForChengQing
            this.whoseTurn = whoseTurn
            this.dyingQueue = dyingQueue
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.receiveOrder = receiveOrder
            this.inFrontOfWhom = inFrontOfWhom
            this.r = r
            this.fsm = fsm
            this.r = r
            this.playerAndCards = playerAndCards
            this.fsm = fsm
            this.selection = selection
            this.fromPlayer = fromPlayer
            this.waitingPlayer = waitingPlayer
            this.card = card
            this.r = r
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.needReturnCount = needReturnCount
            this.fsm = fsm
            this.fsm = fsm
            this.cards = cards
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.color = color
            this.fsm = fsm
            this.color = color
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.fsm = fsm
            this.r = r
            this.target = target
            this.card = card
            this.fsm = fsm
            this.r = r
        }

        companion object {
            private val log = Logger.getLogger(executeDuiZhengXiaYaoA::class.java)
        }
    }

    private class executeDuiZhengXiaYaoB(
        fsm: FightPhaseIdle,
        r: Player,
        cards: Array<Card?>,
        colors: List<color?>,
        defaultSelection: PlayerAndCard
    ) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            log.info(r.toString() + "展示了" + Arrays.toString(cards))
            val g = r.game
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_dui_zheng_xia_yao_b_toc.newBuilder().setEnable(true)
                    for (card in cards) builder.addCards(card!!.toPbCard())
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.waitingSecond = 15
                    if (p === r) {
                        val seq2: Int = p.seq
                        builder.seq = seq2
                        p.setTimeout(GameExecutor.Companion.post(g, Runnable {
                            if (p.checkSeq(seq2)) g.tryContinueResolveProtocol(
                                r, Role.skill_dui_zheng_xia_yao_c_tos.newBuilder()
                                    .setTargetPlayerId(r.getAlternativeLocation(defaultSelection.player.location()))
                                    .setMessageCardId(defaultSelection.card.id).setSeq(seq2).build()
                            )
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    }
                    p.send(builder.build())
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.Companion.post(g, Runnable {
                    g.tryContinueResolveProtocol(
                        r, Role.skill_dui_zheng_xia_yao_c_tos.newBuilder()
                            .setTargetPlayerId(r.getAlternativeLocation(defaultSelection.player.location()))
                            .setMessageCardId(defaultSelection.card.id).build()
                    )
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (player !== r) {
                log.error("不是你发技能的时机")
                return null
            }
            if (message !is Role.skill_dui_zheng_xia_yao_c_tos) {
                log.error("错误的协议")
                return null
            }
            val g = r.game
            if (r is HumanPlayer && !r.checkSeq(message.seq)) {
                log.error("操作太晚了, required Seq: " + r.seq + ", actual Seq: " + message.seq)
                return null
            }
            if (message.targetPlayerId < 0 || message.targetPlayerId >= g.players.size) {
                log.error("目标错误")
                return null
            }
            val target = g.players[r.getAbstractLocation(message.targetPlayerId)]
            if (!target.isAlive) {
                log.error("目标已死亡")
                return null
            }
            val card = target.findMessageCard(message.messageCardId)
            if (card == null) {
                log.error("没有这张牌")
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
                return null
            }
            r.incrSeq()
            log.info(r.toString() + "弃掉了" + target + "面前的" + card)
            g.deck.discard(target.deleteMessageCard(card.id))
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = skill_dui_zheng_xia_yao_c_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    builder.messageCardId = card.id
                    p.send(builder.build())
                }
            }
            fsm.whoseFightTurn = fsm.inFrontOfWhom
            return ResolveResult(fsm, true)
        }

        val fsm: FightPhaseIdle
        val r: Player
        val cards: Array<Card?>
        val colors: List<color?>
        val defaultSelection: PlayerAndCard

        init {
            this.card = card
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            this.card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            this.card = card
            this.player = player
            this.card = card
            this.card = card
            this.drawCards = drawCards
            this.players = players
            this.mainPhaseIdle = mainPhaseIdle
            this.dieSkill = dieSkill
            this.player = player
            this.player = player
            this.onUseCard = onUseCard
            this.game = game
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.dir = dir
            this.targetPlayer = targetPlayer
            this.lockedPlayers = lockedPlayers
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.player = player
            this.whoseTurn = whoseTurn
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.fightPhase = fightPhase
            this.player = player
            this.sendPhase = sendPhase
            this.dieGiveCard = dieGiveCard
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.inFrontOfWhom = inFrontOfWhom
            this.isMessageCardFaceUp = isMessageCardFaceUp
            this.waitForChengQing = waitForChengQing
            this.waitForChengQing = waitForChengQing
            this.whoseTurn = whoseTurn
            this.dyingQueue = dyingQueue
            this.diedQueue = diedQueue
            this.afterDieResolve = afterDieResolve
            this.whoseTurn = whoseTurn
            this.messageCard = messageCard
            this.receiveOrder = receiveOrder
            this.inFrontOfWhom = inFrontOfWhom
            this.r = r
            this.fsm = fsm
            this.r = r
            this.playerAndCards = playerAndCards
            this.fsm = fsm
            this.selection = selection
            this.fromPlayer = fromPlayer
            this.waitingPlayer = waitingPlayer
            this.card = card
            this.r = r
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.needReturnCount = needReturnCount
            this.fsm = fsm
            this.fsm = fsm
            this.cards = cards
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.target = target
            this.fsm = fsm
            this.r = r
            this.target = target
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.fsm = fsm
            this.r = r
            this.color = color
            this.fsm = fsm
            this.color = color
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.fsm = fsm
            this.r = r
            this.target = target
            this.card = card
            this.fsm = fsm
            this.r = r
            this.fsm = fsm
            this.r = r
            this.cards = cards
            this.colors = colors
            this.defaultSelection = defaultSelection
        }

        companion object {
            private val log = Logger.getLogger(executeDuiZhengXiaYaoB::class.java)
        }
    }

    companion object {
        private val log = Logger.getLogger(DuiZhengXiaYao::class.java)
        private fun getSameColors(card1: Card?, card2: Card?): List<color?> {
            val colors: MutableList<color?> = ArrayList()
            for (color in arrayOf(color.Black, color.Red, color.Blue)) {
                if (card1.getColors().contains(color) && card2.getColors().contains(color)) colors.add(color)
            }
            return colors
        }

        private fun findColorMessageCard(game: Game, colors: List<color?>): PlayerAndCard? {
            for (player in game.players) {
                for (card in player.messageCards.values) {
                    for (color in card.colors) {
                        if (colors.contains(color)) return PlayerAndCard(player, card)
                    }
                }
            }
            return null
        }

        fun ai(e: FightPhaseIdle, skill: ActiveSkill): Boolean {
            val player = e.whoseFightTurn
            if (player.isRoleFaceUp) return false
            val playerCount = player.game.players.size
            val playerAndCards: MutableList<PlayerAndCard> = ArrayList()
            for (p in player.game.players) {
                if (p.isAlive) {
                    for (c in p.messageCards.values) playerAndCards.add(PlayerAndCard(p, c))
                }
            }
            if (playerAndCards.size < playerCount) return false
            if (ThreadLocalRandom.current().nextInt(playerCount * playerCount) != 0) return false
            GameExecutor.Companion.post(player.game, Runnable {
                skill.executeProtocol(
                    player.game, player, Role.skill_dui_zheng_xia_yao_a_tos.getDefaultInstance()
                )
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}