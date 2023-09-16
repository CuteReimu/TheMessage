package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.*
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

    override val type = card_type.Shi_Tan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁闭了，不能出牌")
            return false
        }
        if (r.location in g.diaoHuLiShanPlayers) {
            log.error("你被调虎离山了，不能出牌")
            (r as? HumanPlayer)?.sendErrorMessage("你被调虎离山了，不能出牌")
            return false
        }
        if (type in g.qiangLingTypes) {
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
        val resolveFunc = { valid: Boolean ->
            if (valid) {
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
            } else {
                OnFinishResolveCard(
                    r, target, getOriginCard(), card_type.Shi_Tan, MainPhaseIdle(r),
                    discardAfterResolve = false
                )
            }
        }
        g.resolve(OnUseCard(r, r, target, getOriginCard(), card_type.Shi_Tan, resolveFunc, g.fsm!!))
    }

    private fun checkDrawCard(target: Player): Boolean {
        for (i in whoDrawCard) if (i == target.identity) return true
        return false
    }

    private fun notifyResult(target: Player, draw: Boolean) {
        for (player in target.game!!.players) {
            if (player is HumanPlayer) {
                val builder = execute_shi_tan_toc.newBuilder()
                builder.playerId = player.getAlternativeLocation(target.location)
                builder.isDrawCard = draw
                player.send(builder.build())
            }
        }
    }

    private data class executeShiTan(val r: Player, val target: Player, val card: ShiTan) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in r.game!!.players) {
                if (p is HumanPlayer) {
                    val builder = show_shi_tan_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location)
                    builder.targetPlayerId = p.getAlternativeLocation(target.location)
                    builder.waitingSecond = Config.WaitSecond
                    if (p === target) {
                        val seq2 = p.seq
                        builder.setSeq(seq2).card = card.toPbCard()
                        p.timeout = (GameExecutor.post(r.game!!, {
                            if (p.checkSeq(seq2)) {
                                autoSelect()
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
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is execute_shi_tan_tos) {
                log.error("现在正在结算试探：$card")
                (target as? HumanPlayer)?.sendErrorMessage("现在正在结算试探：$card")
                return null
            }
            if (target !== player) {
                log.error("你不是试探的目标：$card")
                (target as? HumanPlayer)?.sendErrorMessage("你不是试探的目标：$card")
                return null
            }
            var discardCard: Card? = null
            if (card.checkDrawCard(target) || target.cards.isEmpty()) {
                if (message.cardIdCount != 0) {
                    log.error("${target}被使用${card}时不应该弃牌")
                    (target as? HumanPlayer)?.sendErrorMessage("${target}被使用${card}时不应该弃牌")
                    return null
                }
            } else {
                if (message.cardIdCount != 1) {
                    log.error("${target}被使用${card}时应该弃一张牌")
                    (target as? HumanPlayer)?.sendErrorMessage("${target}被使用${card}时应该弃一张牌")
                    return null
                }
                discardCard = target.findCard(message.getCardId(0))
                if (discardCard == null) {
                    log.error("没有这张牌")
                    (target as? HumanPlayer)?.sendErrorMessage("没有这张牌")
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
            return ResolveResult(
                OnFinishResolveCard(
                    r, target, card.getOriginCard(), card_type.Shi_Tan, MainPhaseIdle(r),
                    discardAfterResolve = false
                ),
                true
            )
        }

        private fun autoSelect() {
            val builder = execute_shi_tan_tos.newBuilder()
            if (!card.checkDrawCard(target) && target.cards.isNotEmpty())
                builder.addCardId(target.cards.random().id)
            target.game!!.tryContinueResolveProtocol(target, builder.build())
        }

        companion object {
            private val log = Logger.getLogger(executeShiTan::class.java)
        }
    }

    override fun toPbCard(): card {
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
            if (player === player.game!!.jinBiPlayer) return false
            if (player.game!!.qiangLingTypes.contains(card_type.Shi_Tan)) return false
            if (player.location in player.game!!.diaoHuLiShanPlayers) return false
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