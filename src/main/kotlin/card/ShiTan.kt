package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.RobotPlayer.Companion.bestCard
import com.fengsheng.phase.MainPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.protos.*
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.Shi_Tan
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Fengsheng.execute_shi_tan_tos
import com.fengsheng.skill.ConvertCardSkill
import com.fengsheng.skill.SkillId.*
import com.fengsheng.skill.cannotPlayCard
import com.google.protobuf.GeneratedMessage
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
            r.sendErrorMessage("你被禁止使用试探")
            return false
        }
        val target = args[0] as Player
        if (r !== (g.fsm as? MainPhaseIdle)?.whoseTurn) {
            logger.error("试探的使用时机不对")
            r.sendErrorMessage("试探的使用时机不对")
            return false
        }
        if (r === target) {
            logger.error("试探不能对自己使用")
            r.sendErrorMessage("试探不能对自己使用")
            return false
        }
        if (!target.alive) {
            logger.error("目标已死亡")
            r.sendErrorMessage("目标已死亡")
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
                g.players.send {
                    useShiTanToc {
                        playerId = it.getAlternativeLocation(r.location)
                        targetPlayerId = it.getAlternativeLocation(target.location)
                        if (it === r) cardId = id
                    }
                }
                ExecuteShiTan(fsm, r, target, this@ShiTan)
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
        target.game!!.players.send {
            executeShiTanToc {
                playerId = it.getAlternativeLocation(target.location)
                isDrawCard = draw
            }
        }
    }

    private data class ExecuteShiTan(
        val fsm: MainPhaseIdle,
        val r: Player,
        val target: Player,
        val card: ShiTan
    ) : WaitingFsm {
        override val whoseTurn
            get() = fsm.whoseTurn

        override fun resolve(): ResolveResult? {
            r.game!!.players.send { p ->
                showShiTanToc {
                    playerId = p.getAlternativeLocation(r.location)
                    targetPlayerId = p.getAlternativeLocation(target.location)
                    waitingSecond = r.game!!.waitSecond / 2
                    if (p === target) {
                        val seq2 = p.seq
                        seq = seq2
                        card = this@ExecuteShiTan.card.toPbCard()
                        p.timeout = GameExecutor.post(r.game!!, {
                            if (p.checkSeq(seq2)) {
                                autoSelect()
                            }
                        }, p.getWaitSeconds(waitingSecond + 2).toLong(), TimeUnit.SECONDS)
                    } else if (p === r) {
                        card = this@ExecuteShiTan.card.toPbCard()
                    }
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

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (message !is execute_shi_tan_tos) {
                logger.error("现在正在结算试探：$card")
                target.sendErrorMessage("现在正在结算试探：$card")
                return null
            }
            if (target !== player) {
                logger.error("你不是试探的目标：$card")
                target.sendErrorMessage("你不是试探的目标：$card")
                return null
            }
            var discardCard: Card? = null
            if (card.checkDrawCard(target) || target.cards.isEmpty()) {
                if (message.cardIdCount != 0) {
                    logger.error("${target}被使用${card}时不应该弃牌")
                    target.sendErrorMessage("${target}被使用${card}时不应该弃牌")
                    return null
                }
            } else {
                if (message.cardIdCount != 1) {
                    logger.error("${target}被使用${card}时应该弃一张牌")
                    target.sendErrorMessage("${target}被使用${card}时应该弃一张牌")
                    return null
                }
                discardCard = target.findCard(message.getCardId(0))
                if (discardCard == null) {
                    logger.error("没有这张牌")
                    target.sendErrorMessage("没有这张牌")
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
            target.game!!.tryContinueResolveProtocol(target, executeShiTanTos {
                if (!card.checkDrawCard(target) && target.cards.isNotEmpty()) {
                    if (isRobot) cardId.add(target.cards.bestCard(target.identity, true).id)
                    else cardId.add(target.cards.random().id)
                }
            })
        }
    }

    override fun toPbCard(): card {
        return card {
            cardId = id
            cardDir = direction
            canLock = lockable
            cardType = type
            cardColor.addAll(colors)
            whoDrawCard.addAll(this@ShiTan.whoDrawCard)
        }
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
                player.game!!.isEarly -> {
                    if (player.identity != Black && player.identity !in (card as ShiTan).whoDrawCard)
                        return false // 开局不使用-1试探队友
                    player.game!!.players.filter {
                        it !== player && it!!.alive && (!it.roleFaceUp ||
                            (it.findSkill(CHENG_FU) == null && it.findSkill(SHOU_KOU_RU_PING) == null)) &&
                            it.cards.isNotEmpty() // 不对没有手牌的人使用
                    }
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

                player.identity == Black -> {
                    // 按照和自己身份相同的情报数降序排列，然后按照手牌数升序排列
                    val c1: Comparator<Player?> = Comparator { p1, p2 ->
                        val p1MsgCount = p1!!.messageCards.count(p1.identity)
                        val p2MsgCount = p2!!.messageCards.count(p2.identity)
                        if (p1MsgCount != p2MsgCount)
                            return@Comparator p2MsgCount.compareTo(p1MsgCount)
                        p1.cards.size.compareTo(p2.cards.size)
                    }
                    val colors = listOf(Red, Blue).filter { it !in (card as ShiTan).whoDrawCard }
                    if (colors.isEmpty()) return false
                    else {
                        listOf(player.game!!.players.filter {
                            it!!.alive && it.identity in colors && it.cards.isNotEmpty()
                        }.minWithOrNull(c1))
                    }
                }

                jianXianSheng != null && player.isPartner(jianXianSheng) ->
                    listOf(jianXianSheng)

                yaPao != null && player.isPartner(yaPao) && yaPao.getSkillUseCount(SHOU_KOU_RU_PING) == 0 ->
                    listOf(yaPao)

                else -> {
                    player.game!!.players.filter {
                        it !== player && it!!.alive && (!it.roleFaceUp ||
                            (it.findSkill(CHENG_FU) == null && it.findSkill(CONG_RONG_YING_DUI) == null))
                    }.run {
                        filter {
                            it!!.isPartner(player) &&
                                (it.findSkill(SHOU_KOU_RU_PING) != null || it.identity in (card as ShiTan).whoDrawCard)
                        }.ifEmpty {
                            filter {
                                it!!.isEnemy(player) && it.findSkill(SHOU_KOU_RU_PING) == null &&
                                    it.identity !in (card as ShiTan).whoDrawCard && it.cards.isNotEmpty()
                            }
                        }
                    }
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
