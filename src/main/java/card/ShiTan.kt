package com.fengsheng.card

import com.fengsheng.*
import com.fengsheng.card.ShiTanimport

com.fengsheng.card.ShiTan.executeShiTanimport com.fengsheng.phase.MainPhaseIdleimport com.fengsheng.phase.OnUseCardimport com.fengsheng.protos.Commonimport com.fengsheng.protos.Common.*import com.fengsheng.protos.Fengshengimport

com.fengsheng.protos.Fengsheng.show_shi_tan_tocimport com.fengsheng.protos.Fengsheng.use_shi_tan_tocimport com.fengsheng.protos.Role.skill_cheng_fu_tocimport com.fengsheng.protos.Role.skill_jiu_ji_b_tocimport com.fengsheng.skill.Skillimport com.fengsheng.skill.SkillIdimport com.google.protobuf.GeneratedMessageV3import org.apache.log4j.Loggerimport java.util.*import java.util.Listimport

java.util.concurrent.*import kotlin.collections.ArrayListimport

kotlin.collections.HashSetimport kotlin.collections.MutableListimport kotlin.collections.MutableSet
class ShiTan : Card {
    val whoDrawCard: Array<color>

    constructor(
        id: Int,
        colors: Array<color>,
        direction: direction,
        lockable: Boolean,
        whoDrawCard: Array<color>
    ) : super(id, colors, direction, lockable) {
        this.whoDrawCard = whoDrawCard
    }

    constructor(id: Int, card: ShiTan?) : super(id, card) {
        whoDrawCard = card!!.whoDrawCard
    }

    override val type: card_type
        get() = card_type.Shi_Tan

    override fun canUse(g: Game, r: Player, vararg args: Any): Boolean {
        if (r === g.jinBiPlayer) {
            log.error("你被禁闭了，不能出牌")
            return false
        }
        if (g.qiangLingTypes.contains(type)) {
            log.error("试探被禁止使用了")
            return false
        }
        val target = args[0] as Player
        if (g.fsm !is MainPhaseIdle || r !== fsm.player) {
            log.error("试探的使用时机不对")
            return false
        }
        if (r === target) {
            log.error("试探不能对自己使用")
            return false
        }
        if (!target.isAlive) {
            log.error("目标已死亡")
            return false
        }
        return true
    }

    override fun execute(g: Game, r: Player, vararg args: Any) {
        val target = args[0] as Player
        log.info(r.toString() + "对" + target + "使用了" + this)
        r.deleteCard(id)
        val resolveFunc = Fsm {
            if (target.isRoleFaceUp && target.findSkill<Skill?>(SkillId.CHENG_FU) != null) {
                log.info(target.toString() + "触发了[城府]，试探无效")
                for (player in g.players) {
                    if (player is HumanPlayer) {
                        val builder = skill_cheng_fu_toc.newBuilder()
                            .setPlayerId(player.getAlternativeLocation(target.location()))
                        builder.fromPlayerId = player.getAlternativeLocation(r.location())
                        if (player == r || player == target || target.getSkillUseCount(SkillId.JIU_JI) != 1) builder.card =
                            toPbCard() else builder.unknownCardCount = 1
                        player.send(builder.build())
                    }
                }
                if (target.getSkillUseCount(SkillId.JIU_JI) == 1) {
                    target.addSkillUseCount(SkillId.JIU_JI)
                    target.addCard(this)
                    log.info(target.toString() + "将使用的" + this + "加入了手牌")
                    for (player in g.players) {
                        if (player is HumanPlayer) {
                            val builder = skill_jiu_ji_b_toc.newBuilder()
                                .setPlayerId(player.getAlternativeLocation(target.location()))
                            if (player == r || player == target) builder.card =
                                toPbCard() else builder.unknownCardCount = 1
                            player.send(builder.build())
                        }
                    }
                } else {
                    g.deck.discard(this)
                }
                return@Fsm ResolveResult(MainPhaseIdle(r), true)
            }
            for (p in g.players) {
                if (p is HumanPlayer) {
                    val builder = use_shi_tan_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    if (p === r) builder.cardId = id
                    p.send(builder.build())
                }
            }
            ResolveResult(executeShiTan(r, target, this), true)
        }
        g.resolve(OnUseCard(r, r, target, this, card_type.Shi_Tan, r, resolveFunc))
    }

    private fun checkDrawCard(target: Player): Boolean {
        for (i in whoDrawCard) if (i == target.identity) return true
        return false
    }

    private fun notifyResult(target: Player, draw: Boolean) {
        for (player in target.game.players) {
            (player as? HumanPlayer)?.send(
                Fengsheng.execute_shi_tan_toc.newBuilder()
                    .setPlayerId(player.getAlternativeLocation(target.location())).setIsDrawCard(draw).build()
            )
        }
    }

    private class executeShiTan(r: Player, target: Player, val card: ShiTan) : WaitingFsm {
        override fun resolve(): ResolveResult? {
            for (p in r.game.players) {
                if (p is HumanPlayer) {
                    val builder = show_shi_tan_toc.newBuilder()
                    builder.playerId = p.getAlternativeLocation(r.location())
                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                    builder.waitingSecond = 15
                    if (p === target) {
                        val seq2: Int = p.seq
                        builder.setSeq(seq2).card = card.toPbCard()
                        p.setTimeout(GameExecutor.Companion.post(r.game, Runnable {
                            if (p.checkSeq(seq2)) {
                                p.incrSeq()
                                autoSelect()
                                r.game.resolve(MainPhaseIdle(r))
                            }
                        }, p.getWaitSeconds(builder.waitingSecond + 2).toLong(), TimeUnit.SECONDS))
                    } else if (p === r) {
                        builder.card = card.toPbCard()
                    }
                    p.send(builder.build())
                }
            }
            if (target is RobotPlayer) {
                GameExecutor.Companion.post(target.getGame(), Runnable {
                    autoSelect()
                    target.getGame().resolve(MainPhaseIdle(r))
                }, 2, TimeUnit.SECONDS)
            }
            return null
        }

        override fun resolveProtocol(player: Player, message: GeneratedMessageV3): ResolveResult? {
            if (message !is Fengsheng.execute_shi_tan_tos) {
                log.error("现在正在结算试探：$card")
                return null
            }
            if (target !== player) {
                log.error("你不是试探的目标：$card")
                return null
            }
            if (card.checkDrawCard(target) || target.cards.isEmpty()) {
                if (message.cardIdCount != 0) {
                    log.error(target.toString() + "被使用" + card + "时不应该弃牌")
                    return null
                }
            } else {
                if (message.cardIdCount != 1) {
                    log.error(target.toString() + "被使用" + card + "时应该弃一张牌")
                    return null
                }
            }
            player.incrSeq()
            if (card.checkDrawCard(target)) {
                log.info(target.toString() + "选择了[摸一张牌]")
                card.notifyResult(target, true)
                target.draw(1)
            } else {
                log.info(target.toString() + "选择了[弃一张牌]")
                card.notifyResult(target, false)
                if (message.cardIdCount > 0) target.game.playerDiscardCard(
                    target,
                    target.findCard(message.getCardId(0))
                )
            }
            return ResolveResult(MainPhaseIdle(r), true)
        }

        private fun autoSelect() {
            val builder = Fengsheng.execute_shi_tan_tos.newBuilder()
            if (!card.checkDrawCard(target) && !target.cards.isEmpty()) {
                for (cardId in target.cards.keys) {
                    builder.addCardId(cardId)
                    break
                }
            }
            resolveProtocol(target, builder.build())
        }

        val r: Player
        val target: Player

        init {
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            card = card
        }

        companion object {
            private val log = Logger.getLogger(executeShiTan::class.java)
        }
    }

    override fun toPbCard(): card? {
        return card.newBuilder().setCardId(id).setCardDir(direction).setCanLock(lockable).setCardType(
            type
        ).addAllCardColor(colors).addAllWhoDrawCard(Arrays.asList(*whoDrawCard)).build()
    }

    override fun toString(): String {
        val color: String = Card.Companion.cardColorToString(colors)
        if (whoDrawCard.size == 1) return Player.Companion.identityColorToString(whoDrawCard[0]) + "+1试探"
        val set: MutableSet<color> = HashSet(List.of(Common.color.Black, Common.color.Red, Common.color.Blue))
        set.remove(whoDrawCard[0])
        set.remove(whoDrawCard[1])
        for (whoDiscardCard in set) {
            return color + Player.Companion.identityColorToString(whoDiscardCard) + "-1试探"
        }
        throw RuntimeException("impossible whoDrawCard: " + Arrays.toString(whoDrawCard))
    }

    companion object {
        private val log = Logger.getLogger(ShiTan::class.java)
        fun ai(e: MainPhaseIdle, card: Card): Boolean {
            val player = e.player
            val players: MutableList<Player> = ArrayList()
            for (p in player.game.players) if (p !== player && p.isAlive && (!p.isRoleFaceUp || p.findSkill<Skill?>(
                    SkillId.CHENG_FU
                ) == null)
            ) players.add(p)
            if (players.isEmpty()) return false
            val p = players[ThreadLocalRandom.current().nextInt(players.size)]
            GameExecutor.Companion.post(
                player.game,
                Runnable { card.execute(player.game, player, p) },
                2,
                TimeUnit.SECONDS
            )
            return true
        }
    }
}