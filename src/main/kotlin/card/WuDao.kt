package com.fengsheng.card

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.HumanPlayer
import com.fengsheng.Player
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.OnFinishResolveCard
import com.fengsheng.phase.OnUseCard
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng.use_wu_dao_toc
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class WuDao : Card {
    constructor(id: Int, colors: List<color>, direction: direction, lockable: Boolean) :
            super(id, colors, direction, lockable)

    constructor(id: Int, card: Card) : super(id, card)

    /**
     * 仅用于“作为误导使用”
     */
    internal constructor(originCard: Card) : super(originCard)

    override val type = card_type.Wu_Dao

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
            log.error("误导被禁止使用了")
            (r as? HumanPlayer)?.sendErrorMessage("误导被禁止使用了")
            return false
        }
        val target = args[0] as Player
        val fsm = g.fsm as? FightPhaseIdle
        if (fsm == null) {
            log.error("误导的使用时机不对")
            (r as? HumanPlayer)?.sendErrorMessage("误导的使用时机不对")
            return false
        }
        val left = fsm.inFrontOfWhom.getNextLeftAlivePlayer()
        val right = fsm.inFrontOfWhom.getNextRightAlivePlayer()
        if (target === fsm.inFrontOfWhom || target !== left && target !== right) {
            log.error("误导只能选择情报当前人左右两边的人作为目标")
            (r as? HumanPlayer)?.sendErrorMessage("误导只能选择情报当前人左右两边的人作为目标")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        log.info("${r}对${target}使用了$this")
        val fsm = g.fsm as FightPhaseIdle
        r.deleteCard(id)
        val resolveFunc = { valid: Boolean ->
            if (valid) {
                for (player in g.players) {
                    if (player is HumanPlayer) {
                        val builder = use_wu_dao_toc.newBuilder()
                        builder.card = toPbCard()
                        builder.playerId = player.getAlternativeLocation(r.location)
                        builder.targetPlayerId = player.getAlternativeLocation(target.location)
                        player.send(builder.build())
                    }
                }
                val newFsm = fsm.copy(inFrontOfWhom = target, whoseFightTurn = target)
                OnFinishResolveCard(r, target, this, card_type.Wu_Dao, newFsm)
            } else {
                val newFsm = fsm.copy(whoseFightTurn = fsm.inFrontOfWhom)
                OnFinishResolveCard(r, target, this, card_type.Wu_Dao, newFsm)
            }
        }
        g.resolve(OnUseCard(fsm.whoseTurn, r, target, this, card_type.Wu_Dao, resolveFunc, fsm))
    }

    override fun toString(): String {
        return "${cardColorToString(colors)}误导"
    }

    companion object {
        private val log = Logger.getLogger(WuDao::class.java)
        fun ai(e: FightPhaseIdle, card: Card): Boolean {
            val player = e.whoseFightTurn
            if (player === player.game!!.jinBiPlayer) return false
            if (player.game!!.qiangLingTypes.contains(card_type.Wu_Dao)) return false
            if (player.location in player.game!!.diaoHuLiShanPlayers) return false
            val left = e.inFrontOfWhom.getNextLeftAlivePlayer()
            val right = e.inFrontOfWhom.getNextRightAlivePlayer()
            var target: Player? = null
            if (player.identity != color.Black) {
                val enemyColor = arrayOf(color.Red, color.Black).first { it != player.identity }
                if (card.isPureBlack()) { // 纯黑
                    if (player.isEnemy(e.inFrontOfWhom)) return false // 不在队友面前，不使用误导
                    target = arrayOf(left, right).run {
                        find { it.identity == enemyColor && it.messageCards.count(color.Black) == 2 } // 2黑敌对阵营
                            ?: find { it.identity == enemyColor } // 敌对阵营
                            ?: find { player.identity == color.Black } // 神秘人
                            ?: find {
                                e.inFrontOfWhom.messageCards.count(color.Black) == 2
                                        && it.messageCards.count(color.Black) < 2
                            } // 2黑时，无2黑队友
                    }
                    if (target != null && player.checkFriendship(target, e.inFrontOfWhom) > 0) target = null
                } else if (card.colors.size == 1 && card.colors.first() == player.identity) { // 己方纯色
                    if (player.isPartnerOrSelf(e.inFrontOfWhom)) return false // 在队友面前，不使用误导
                    target = arrayOf(left, right).run {
                        find { it.identity == player.identity && it.messageCards.count(player.identity) == 2 } // 2真队友
                            ?: find { it.identity == player.identity } // 队友
                    }
                    if (target != null && player.checkFriendship(target, e.inFrontOfWhom) < 0) target = null
                } else if (card.colors.size == 1 && card.colors.first() == enemyColor) { // 敌方纯色
                    if (e.inFrontOfWhom.identity != enemyColor) return false // 不在敌对阵营面前，不使用误导
                    target = arrayOf(left, right).run {
                        find { it.identity == player.identity } // 队友
                            ?: find { it.identity == color.Black } // 神秘人
                            ?: find { it.identity == player.identity } // 队友
                            ?: find {
                                e.inFrontOfWhom.messageCards.count(enemyColor) == 2
                                        && it.messageCards.count(enemyColor) < 2
                            } // 2真时，无2真敌人
                    }
                    if (target != null && player.checkFriendship(target, e.inFrontOfWhom) < 0) target = null
                }
            }
            val colors = e.messageCard.colors
            if (e.inFrontOfWhom === player && colors.size == 1 && colors.first() != color.Black)
                return false
            if (target == null) {
                target = when (Random.nextInt(8)) {
                    0 -> left
                    1 -> right
                    else -> return false
                }
            }
            GameExecutor.post(player.game!!, {
                val card0 = if (card.type == card_type.Wu_Dao) card else falseCard(card_type.Wu_Dao, card)
                card0.execute(player.game!!, player, target)
            }, 2, TimeUnit.SECONDS)
            return true
        }
    }
}