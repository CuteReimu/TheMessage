package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.show_shi_tan_toc
import com.fengsheng.protos.Fengsheng.use_shi_tan_toc
import com.fengsheng.protos.Role.skill_cheng_fu_toc
import com.fengsheng.protos.Role.skill_jiu_ji_b_toc
import com.fengsheng.skill.SkillId
import com.google.protobuf.GeneratedMessageV3
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class ShiTan : Card {
    private val whoDrawCard: List<color>

    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean, whoDrawCard: List<color>) :
            super(id, colors, direction, lockable) {
        this.whoDrawCard = whoDrawCard
    }

    constructor(id: Int, card: ShiTan) : super(id, card) {
        whoDrawCard = card.whoDrawCard
    }

    override val type: card_type
        get() = card_type.Shi_Tan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("试探被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("试探被禁止使用了")
            return false
        }
        val target = args[0] as Player
        if (r !== (g.fsm as? MainPhaseIdle)?.player) {
            log.error("试探的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("试探的使用时机不对")
            return false
        }
        if (r === target) {
            log.error("试探不能对自己使用")
            (r as? HumanPlayer)?.sendErrorMessage("试探不能对自己使用")
            return false
        }
        if (!target.alive) {
            log.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        log.info("${r}对${target}使用了$this")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            if (target.roleFaceUp && target.findSkill(SkillId.CHENG_FU) != null) {
                log.info("${target}触发了[城府]，试探无效")
                for (player in g.players) {
                    if (player is HumanPlayer) {
                        val builder = skill_cheng_fu_toc.newBuilder()
                            .setPlayerId(player.getAlternativeLocation(target.location))
                        builder.fromPlayerId = player.getAlternativeLocation(r.location)
                        if (player == r || player == target || target.getSkillUseCount(SkillId.JIU_JI) != 1) builder.card =
                            toPbCard() else builder.unknownCardCount = 1
                        player.send(builder.build())
                    }
                }
                if (target.getSkillUseCount(SkillId.JIU_JI) == 1) {
                    target.addSkillUseCount(SkillId.JIU_JI)
                    target.cards.add(this@ShiTan)
                    log.info(target.toString() + "将使用的${this@ShiTan}加入了手牌")
                    for (player in g.players) {
                        if (player is HumanPlayer) {
                            val builder = skill_jiu_ji_b_toc.newBuilder()
                                .setPlayerId(player.getAlternativeLocation(target.location))
                            if (player == r || player == target) builder.card = toPbCard()
                            else builder.unknownCardCount = 1
                            player.send(builder.build())
                        }
                    }
                } else {
                    g.deck.discard(this@ShiTan.getOriginCard())
                }
                MainPhaseIdle(r)
            } else {
                for (p in g.players) {
                    if (p is HumanPlayer) {
                        val builder = use_shi_tan_toc.newBuilder()
                        builder.playerId = p.getAlternativeLocation(r.location)
                        builder.targetPlayerId = p.getAlternativeLocation(target.location)
                        if (p === r) builder.cardId = id
                        p.send(builder.build())
                    }
                }
                executeShiTan(r, target, this@ShiTan)
            }
        }
        g.resolve(OnUseCard(r, r, target, this, card_type.Shi_Tan, r, resolveFunc, g.fsm!!))
    }

    private fun checkDrawCard(target: Player): Boolean {
        for (i in whoDrawCard) if (i == target.identity) return true
        return false
    }

    private fun notifyResult(target: Player, draw: Boolean) {
        for (player in target.game!!.players) {
            (player as? HumanPlayer)?.send(
                Fengsheng.execute_shi_tan_toc.newBuilder()
                    .setPlayerId(player.getAlternativeLocation(target.location)).setIsDrawCard(draw).build()
            )
        }
    }

    private data class executeShiTan(val r: Player, val target: Player, val card: ShiTan) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = show_shi_tan_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = 15
                    if (p === target) {
                        val seq2: Int = p.seq
                        builder.setSeq(seq2).card = card.toPbCard()
                        p.timeout = (GameExecutor.post(r.game!!, {
                            if (p.checkSeq(seq2)) {
                                p.incrSeq()
                                autoSelect()
                                r.game!!.resolve(MainPhaseIdle(r))
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    } else if (p === r) {
                        builder.card = card.toPbCard()
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer) {
                GameExecutor.post(target.game!!, {
                    autoSelect()
                    target.game!!.resolve(MainPhaseIdle(r))
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is Fengsheng.execute_shi_tan_tos) {
                log.error("现在正在结算试探：$card")
                (r as? HumanPlayer)?.sendErrorMessage("现在正在结算试探：$card")
                return null
            }
            if (target !== player) {
                log.error("你不是试探的目标：$card")
                (r as? HumanPlayer)?.sendErrorMessage("你不是试探的目标：$card")
                return null
            }
            var discardCard: Card? = null
            if (card.checkDrawCard(target) || target.cards.isEmpty()) {
                if (message.cardIdCount != 0) {
                    log.error("${target}被使用${card}时不应该弃牌")
                    (r as? HumanPlayer)?.sendErrorMessage("${target}被使用${card}时不应该弃牌")
                    return null
                }
            } else {
                if (message.cardIdCount != 1) {
                    log.error("${target}被使用${card}时应该弃一张牌")
                    (r as? HumanPlayer)?.sendErrorMessage("${target}被使用${card}时应该弃一张牌")
                    return null
                }
                discardCard = target.findCard(message.getCardId(0))
                if (discardCard == null) {
                    log.error("没有这张牌")
                    (r as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return null
                }
            }
            player.incrSeq()
            if (card.checkDrawCard(target)) {
                log.info("${target}选择了[摸一张牌]")
                card.notifyResult(target, true)
                target.draw(1)
            } else {
                log.info("${target}选择了[弃一张牌]")
                card.notifyResult(target, false)
                if (discardCard != null)
                    target.game!!.playerDiscardCard(target, discardCard)
            }
            return ResolveResult(MainPhaseIdle(r), true)
        }

        private fun autoSelect() {
            val builder = Fengsheng.execute_shi_tan_tos.newBuilder()
            if (!card.checkDrawCard(target) && target.cards.isNotEmpty()) {
                for (c in target.cards) {
                    builder.addCardId(c.id)
                    break
                }
            }
            resolveProtocol(target, builder.build())
        }

        companion object {
            private val log = Logger.getLogger(executeShiTan::class.java)
        }
    }

    override fun toPbCard(): card? {
        val builder = card.newBuilder()
        builder.cardId = id
        builder.cardDir = direction
        builder.canLock = lockable
        builder.cardType = type
        builder.addAllCardColor(colors)
        builder.addAllWhoDrawCard(whoDrawCard)
        return builder.build()
    }

    override fun toString(): String {
        val color: String = cardColorToString(colors)
        if (whoDrawCard.size == 1) return Player.identityColorToString(whoDrawCard[0]) + "+1试探"
        val set = hashSetOf(Common.color.Black, Common.color.Red, Common.color.Blue)
        set.remove(whoDrawCard[0])
        set.remove(whoDrawCard[1])
        for (whoDiscardCard in set) {
            return color + Player.identityColorToString(whoDiscardCard) + "-1试探"
        }
        throw RuntimeException("impossible whoDrawCard: ${whoDrawCard.toTypedArray().contentToString()}")
    }

    companion object {
        private val log = Logger.getLogger(ShiTan::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            val players = player.game!!.players.filter {
                it !== player && it!!.alive && (!it.roleFaceUp || it.findSkill(SkillId.CHENG_FU) == null)
            }
            if (players.isEmpty()) return false
            val p = players[Random.nextInt(players.size)]!!
            GameExecutor.post(player.game!!, { card.execute(player.game!!, player, p) }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}