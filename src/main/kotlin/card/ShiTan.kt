package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Fengsheng.*
import com.fengsheng.skill.ConvertCardSkill
import com.fengsheng.skill.SkillId.*
import com.fengsheng.skill.cannotPlayCard
import com.google.protobuf.GeneratedMessageV3
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class ShiTan : Card {
    private val whoDrawCard: List<color>

    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean, whoDrawCard: List<color>) :
            super(id, colors, direction, lockable) {
        this.whoDrawCard = whoDrawCard
    }

    constructor(id: Int, card: ShiTan) : super(id, card) {
        whoDrawCard = card.whoDrawCard
    }

    override val type = Shi_Tan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用试探")
            (r as? HumanPlayer)?.sendErrorMessage("你被禁止使用试探")
            return false
        }
        val target = args[0] as Player
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("试探的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("试探的使用时机不对")
            return false
        }
        if (r === target) {
            logger.error("试探不能对自己使用")
            (r as? HumanPlayer)?.sendErrorMessage("试探不能对自己使用")
            return false
        }
        if (!target.alive) {
            logger.error("目标已死亡")
            (r as? HumanPlayer)?.sendErrorMessage("目标已死亡")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        val fsm = g.fsm as MainPhaseIdle
        logger.info("${r}对${target}使用了$this")
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
                executeShiTan(fsm, r, target, this@ShiTan)
            } else {
                OnFinishResolveCard(
                    r, r, target, getOriginCard(), Shi_Tan, fsm,
                    discardAfterResolve = false
                )
            }
        }
        g.resolve(ResolveCard(r, r, target, getOriginCard(), Shi_Tan, resolveFunc, fsm))
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

    private data class executeShiTan(
        val fsm: MainPhaseIdle,
        val r: Player,
        val target: Player,
        val card: ShiTan
    ) : WaitingFsm {
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
                        p.timeout = GameExecutor.post(r.game!!, {
                            if (p.checkSeq(seq2)) {
                                autoSelect()
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    } else if (p === r) {
                        builder.card = card.toPbCard()
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer) {
                if (card.checkDrawCard(target) || target.cards.isEmpty()) {
                    GameExecutor.post(target.game!!, {
                        autoSelect(true)
                    }, 100, TimeUnit.MILLISECONDS)
                } else {
                    GameExecutor.post(target.game!!, {
                        autoSelect(true)
                    }, 1, TimeUnit.SECONDS)
                }
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is execute_shi_tan_tos) {
                logger.error("现在正在结算试探：$card")
                (target as? HumanPlayer)?.sendErrorMessage("现在正在结算试探：$card")
                return null
            }
            if (target !== player) {
                logger.error("你不是试探的目标：$card")
                (target as? HumanPlayer)?.sendErrorMessage("你不是试探的目标：$card")
                return null
            }
            var discardCard: Card? = null
            if (card.checkDrawCard(target) || target.cards.isEmpty()) {
                if (message.cardIdCount != 0) {
                    logger.error("${target}被使用${card}时不应该弃牌")
                    (target as? HumanPlayer)?.sendErrorMessage("${target}被使用${card}时不应该弃牌")
                    return null
                }
            } else {
                if (message.cardIdCount != 1) {
                    logger.error("${target}被使用${card}时应该弃一张牌")
                    (target as? HumanPlayer)?.sendErrorMessage("${target}被使用${card}时应该弃一张牌")
                    return null
                }
                discardCard = target.findCard(message.getCardId(0))
                if (discardCard == null) {
                    logger.error("没有这张牌")
                    (target as? HumanPlayer)?.sendErrorMessage("没有这张牌")
                    return null
                }
            }
            player.incrSeq()
            if (card.checkDrawCard(target)) {
                logger.info("${target}选择了[摸一张牌]")
                card.notifyResult(target, true)
                target.draw(1)
            } else {
                logger.info("${target}选择了[弃一张牌]")
                card.notifyResult(target, false)
                if (discardCard != null) {
                    target.game!!.playerDiscardCard(target, discardCard)
                    target.game!!.addEvent(DiscardCardEvent(r, target))
                }
            }
            return ResolveResult(
                OnFinishResolveCard(
                    r, r, target, card.getOriginCard(), Shi_Tan, fsm,
                    discardAfterResolve = false
                ),
                true
            )
        }

        private fun autoSelect(isRobot: Boolean = false) {
            val builder = execute_shi_tan_tos.newBuilder()
            if (!card.checkDrawCard(target) && target.cards.isNotEmpty()) {
                if (isRobot) builder.addCardId(target.cards.bestCard(target.identity, true).id)
                else builder.addCardId(target.cards.random().id)
            }
            target.game!!.tryContinueResolveProtocol(target, builder.build())
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
        val color = cardColorToString(colors)
        if (whoDrawCard.size == 1) return color + Player.identityColorToString(whoDrawCard.first()) + "+1试探"
        listOf(Black, Red, Blue).find { it !in whoDrawCard }?.let {
            return color + Player.identityColorToString(it) + "-1试探"
        }
        throw RuntimeException("impossible whoDrawCard: ${whoDrawCard.joinToString()}")
    }

    companion object {
        fun ai(e: MainPhaseIdle, card: Card, convertCardSkill: ConvertCardSkill?): Boolean {
            val player = e.whoseTurn
            !player.cannotPlayCard(Shi_Tan) || return false
            val yaPao = player.game!!.players.find {
                it!!.alive && it.findSkill(SHOU_KOU_RU_PING) != null
            }
            val jianXianSheng = player.game!!.players.find {
                it!!.alive && it.findSkill(CONG_RONG_YING_DUI) != null
            }
            val p = when {
                player.game!!.isEarly ->
                    player.game!!.players.filter {
                        it !== player && it!!.alive && (!it.roleFaceUp ||
                                (it.findSkill(CHENG_FU) == null && it.findSkill(SHOU_KOU_RU_PING) == null)) &&
                                it.cards.isNotEmpty() // 不对没有手牌的人使用
                    }

                yaPao === player || jianXianSheng === player ->
                    player.game!!.players.run {
                        filter { it!!.alive && it.isPartner(player) }.run {
                            filter { it === jianXianSheng || it === yaPao }.ifEmpty { this }
                        }.ifEmpty {
                            filter { it !== player && it!!.alive }.run {
                                filterNot { it === jianXianSheng || it === yaPao }.ifEmpty { this }
                            }
                        }
                    }

                jianXianSheng != null && player.isPartner(jianXianSheng) ->
                    listOf(jianXianSheng)

                yaPao != null && player.isPartner(yaPao) && yaPao.getSkillUseCount(SHOU_KOU_RU_PING) == 0 ->
                    listOf(yaPao)

                else ->
                    player.game!!.players.filter {
                        it !== player && it!!.alive && (!it.roleFaceUp ||
                                (it.findSkill(CHENG_FU) == null && it.findSkill(SHOU_KOU_RU_PING) == null &&
                                        it.findSkill(CONG_RONG_YING_DUI) == null)) &&
                                it.isEnemy(player) != it.identity in (card as ShiTan).whoDrawCard && // 敌人弃牌，队友摸牌
                                !(it.isEnemy(player) && it.cards.isEmpty()) // 不对没有手牌的敌人使用
                    }
            }.randomOrNull() ?: return false
            GameExecutor.post(player.game!!, {
                convertCardSkill?.onConvert(player)
                card.asCard(Shi_Tan).execute(player.game!!, player, p)
            }, 3, TimeUnit.SECONDS)
            return true
        }
    }
}