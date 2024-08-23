package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.ResolveCard
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.protos.Common.card_type.Po_Yi
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.role.*
import com.fengsheng.protos.Fengsheng.po_yi_show_tos
import com.fengsheng.protos.poYiShowToc
import com.fengsheng.protos.poYiShowTos
import com.fengsheng.protos.usePoYiToc
import com.fengsheng.skill.ConvertCardSkill
import com.fengsheng.skill.SkillId.HUAN_RI
import com.fengsheng.skill.cannotPlayCard
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit

class PoYi : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
        super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为破译使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = Po_Yi

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r.cannotPlayCard(type)) {
            logger.error("你被禁止使用破译")
            r.sendErrorMessage("你被禁止使用破译")
            return false
        }
        val fsm = g.fsm as? SendPhaseIdle
        if (r !== fsm?.inFrontOfWhom) {
            logger.error("破译的使用时机不对")
            r.sendErrorMessage("破译的使用时机不对")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val fsm = g.fsm as SendPhaseIdle
        logger.info("${r}使用了$this")
        r.deleteCard(id)
        val resolveFunc = { _: Boolean ->
            ExecutePoYi(this@PoYi, fsm)
        }
        g.resolve(ResolveCard(fsm.whoseTurn, r, null, getOriginCard(), Po_Yi, resolveFunc, fsm))
    }

    private data class ExecutePoYi(val card: PoYi, val sendPhase: SendPhaseIdle) : WaitingFsm {
        override val whoseTurn
            get() = sendPhase.whoseTurn

        override fun resolve(): ResolveResult? {
            val r = sendPhase.inFrontOfWhom
            r.game!!.players.send { player ->
                usePoYiToc {
                    card = this@ExecutePoYi.card.toPbCard()
                    playerId = player.getAlternativeLocation(r.location)
                    if (!sendPhase.isMessageCardFaceUp) waitingSecond = r.game!!.waitSecond
                    if (player === r) {
                        val seq2 = player.seq
                        messageCard = sendPhase.messageCard.toPbCard()
                        seq = seq2
                        val waitingSecond =
                            if (this.waitingSecond == 0) 0
                            else player.getWaitSeconds(this.waitingSecond + 2)
                        player.timeout = GameExecutor.post(r.game!!, {
                            if (player.checkSeq(seq2))
                                r.game!!.tryContinueResolveProtocol(r, poYiShowTos { })
                        }, waitingSecond.toLong(), TimeUnit.SECONDS)
                    }
                }
            }
            if (r is RobotPlayer) {
                GameExecutor.post(r.game!!, {
                    r.game!!.tryContinueResolveProtocol(r, poYiShowTos { show = sendPhase.messageCard.isBlack() })
                }, 1, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessage): ResolveResult? {
            if (message !is po_yi_show_tos) {
                logger.error("现在正在结算破译")
                player.sendErrorMessage("现在正在结算破译")
                return null
            }
            if (player !== sendPhase.inFrontOfWhom) {
                logger.error("你不是破译的使用者")
                player.sendErrorMessage("你不是破译的使用者")
                return null
            }
            if (message.show && !sendPhase.messageCard.isBlack()) {
                logger.error("非黑牌不能翻开")
                player.sendErrorMessage("非黑牌不能翻开")
                return null
            }
            player.incrSeq()
            showAndDrawCard(message.show)
            val newFsm = if (message.show) sendPhase.copy(isMessageCardFaceUp = true) else sendPhase
            return ResolveResult(
                OnFinishResolveCard(sendPhase.whoseTurn, player, null, card.getOriginCard(), Po_Yi, newFsm),
                true
            )
        }

        private fun showAndDrawCard(show: Boolean) {
            val r = sendPhase.inFrontOfWhom
            if (show) {
                logger.info("${sendPhase.messageCard}被翻开了")
                r.draw(1)
            }
            r.game!!.players.send {
                poYiShowToc {
                    playerId = it.getAlternativeLocation(r.location)
                    this.show = show
                    if (show) messageCard = sendPhase.messageCard.toPbCard()
                }
            }
        }
    }

    override fun toString() = "${cardColorToString(colors)}破译"

    companion object {
        fun ai(e: SendPhaseIdle, card: Card, convertCardSkill: ConvertCardSkill?): Boolean {
            val player = e.inFrontOfWhom
            card.type == Po_Yi || return false // 机器人不要把别的牌当破译用
            !player.cannotPlayCard(Po_Yi) || return false
            // 郑文先面朝上时，手里有破译就出
            player.findSkill(HUAN_RI) != null && player.roleFaceUp ||
                !e.isMessageCardFaceUp && e.messageCard.isBlack() ||
                return false
            GameExecutor.post(player.game!!, {
                convertCardSkill?.onConvert(player)
                card.asCard(Po_Yi).execute(player.game!!, player)
            }, 1, TimeUnit.SECONDS)
            return true
        }
    }
}
