package com.fengsheng

import com.fengsheng.card.*
import com.fengsheng.phase.*
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.notify_die_give_card_toc
import com.fengsheng.skill.*
import org.apache.log4j.Logger
import java.util.concurrent.TimeUnit
import java.util.function.BiPredicate
import java.util.function.Predicate
import kotlin.random.Random

class RobotPlayer : Player() {
    override fun notifyAddHandCard(location: Int, unknownCount: Int, vararg cards: Card) {
        // Do nothing
    }

    override fun notifyDrawPhase() {
        // Do nothing
    }

    override fun notifyMainPhase(waitSecond: Int) {
        val fsm = game!!.fsm as MainPhaseIdle
        if (this !== fsm.player) return
        for (skill in skills) {
            val ai = aiSkillMainPhase[skill.skillId]
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        if (cards.size > 1 && findSkill(SkillId.JI_SONG) == null && (findSkill(SkillId.GUANG_FA_BAO) == null || roleFaceUp)) {
            for (card in cards) {
                val ai = aiMainPhase[card.type]
                if (ai != null && ai.test(fsm, card)) return
            }
        }
        GameExecutor.post(game!!, { game!!.resolve(SendPhaseStart(this)) }, 2, TimeUnit.SECONDS)
    }

    override fun notifySendPhaseStart(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseStart
        if (this !== fsm.player) return
        GameExecutor.post(game!!, { autoSendMessageCard(this) }, 2, TimeUnit.SECONDS)
    }

    override fun notifySendMessageCard(
        player: Player,
        targetPlayer: Player,
        lockedPlayers: Array<Player>,
        messageCard: Card,
        dir: direction?,
        sender: Player
    ) {
        // Do nothing
    }

    override fun notifySendPhase(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseIdle
        if (this !== fsm.inFrontOfWhom) return
        if (this !== game!!.jinBiPlayer) {
            for (card in cards) {
                val ai = aiSendPhase[card.type]
                if (ai != null && ai.test(fsm, card)) return
            }
        }
        GameExecutor.post(game!!, {
            val colors = fsm.messageCard.colors
            val receive = fsm.lockedPlayers.contains(this) || fsm.sender === this || // 如果被锁了，或者自己是传出者，则必须接收
                    if (colors.size == 1) { // 如果是单色，纯黑则不接，纯非黑则有一半几率接，已翻开的纯非黑则必接
                        colors.first() != color.Black && (fsm.isMessageCardFaceUp || Random.nextBoolean())
                    } else {
                        Random.nextInt(4) == 0 // 如果是双色，则有四分之一几率接
                    }
            game!!.resolve(
                if (receive)
                    OnChooseReceiveCard(fsm.whoseTurn, fsm.messageCard, fsm.inFrontOfWhom, fsm.isMessageCardFaceUp)
                else
                    MessageMoveNext(fsm)
            )
        }, 2, TimeUnit.SECONDS)
    }

    override fun notifyChooseReceiveCard(player: Player) {
        // Do nothing
    }

    override fun notifyFightPhase(waitSecond: Int) {
        val fsm = game!!.fsm as FightPhaseIdle
        if (this !== fsm.whoseFightTurn) return
        for (skill in skills) {
            val ai = aiSkillFightPhase[skill.skillId]
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        for (card in cards) {
            var cardType: card_type? = card.type
            if (findSkill(SkillId.YING_BIAN) != null && cardType == card_type.Jie_Huo) cardType = card_type.Wu_Dao
            val ai = aiFightPhase[cardType]
            if (ai != null && ai.test(fsm, card)) return
        }
        GameExecutor.post(game!!, { game!!.resolve(FightPhaseNext(fsm)) }, 2, TimeUnit.SECONDS)
    }

    override fun notifyReceivePhase() {
        // Do nothing
    }

    override fun notifyReceivePhase(
        whoseTurn: Player,
        inFrontOfWhom: Player,
        messageCard: Card,
        waitingPlayer: Player,
        waitSecond: Int
    ) {
        if (waitingPlayer !== this) return
        for (skill in skills) {
            val ai = aiSkillReceivePhase[skill.skillId]
            if (ai != null && ai.test(game!!.fsm!!)) return
        }
        GameExecutor.TimeWheel.newTimeout({
            game!!.tryContinueResolveProtocol(
                this,
                Fengsheng.end_receive_phase_tos.getDefaultInstance()
            )
        }, 2, TimeUnit.SECONDS)
    }

    override fun notifyWin(declareWinners: Array<Player>, winners: Array<Player>) {
        // Do nothing
    }

    override fun notifyAskForChengQing(whoDie: Player, askWhom: Player, waitSecond: Int) {
        val fsm = game!!.fsm as WaitForChengQing
        if (askWhom !== this) return
        run {
            if (identity == color.Black || identity != whoDie.identity) return@run
            val card = cards.find { it is ChengQing } ?: return@run
            val black = whoDie.messageCards.filter { color.Black in it.colors }.run {
                find { it.colors.size == 1 } ?: find { identity !in it.colors } ?: firstOrNull()
            } ?: return@run
            GameExecutor.post(game!!, { card.execute(game!!, this, whoDie, black.id) }, 2, TimeUnit.SECONDS)
            return
        }
        GameExecutor.post(game!!, { game!!.resolve(WaitNextForChengQing(fsm)) }, 2, TimeUnit.SECONDS)
    }

    override fun waitForDieGiveCard(whoDie: Player, waitSecond: Int) {
        val fsm = game!!.fsm as WaitForDieGiveCard
        if (whoDie !== this) return
        GameExecutor.post(game!!, {
            if (identity != color.Black) {
                val target = game!!.players.find { it !== this && it!!.identity == identity }
                if (target != null) {
                    val giveCards = cards.take(3)
                    if (giveCards.isNotEmpty()) {
                        cards.removeAll(giveCards.toSet())
                        target.cards.addAll(giveCards)
                        log.info("${this}给了${target}${giveCards.toTypedArray().contentToString()}")
                        for (p in game!!.players) {
                            if (p is HumanPlayer) {
                                val builder = notify_die_give_card_toc.newBuilder()
                                builder.playerId = p.getAlternativeLocation(location)
                                builder.targetPlayerId = p.getAlternativeLocation(target.location)
                                if (p === target) {
                                    giveCards.forEach { builder.addCard(it.toPbCard()) }
                                } else {
                                    builder.unknownCardCount = giveCards.size
                                }
                                p.send(builder.build())
                            }
                        }
                    }
                }
            }
            game!!.resolve(AfterDieGiveCard(fsm))
        }, 2, TimeUnit.SECONDS)
    }

    companion object {
        private val log = Logger.getLogger(RobotPlayer::class.java)

        /**
         * 随机选择一张牌作为情报传出
         */
        fun autoSendMessageCard(r: Player) {
            val isRobot = r is RobotPlayer
            val card =
                if (!isRobot || r.identity == color.Black) r.cards.first()
                else r.cards.run { find { r.identity in it.colors } ?: find { it.isPureBlack() } ?: first() }
            val fsm = r.game!!.fsm as SendPhaseStart
            val dir =
                if (r.findSkill(SkillId.LIAN_LUO) == null) card.direction
                else arrayOf(direction.Up, direction.Left, direction.Right).random()
            val availableTargets = r.game!!.players.filter { it !== r && it!!.alive }
            val target: Player
            val lockedPlayers = ArrayList<Player>()
            if (dir == direction.Up) {
                target =
                    if (!isRobot || card.isBlack()) availableTargets.random()!!
                    else availableTargets.filter { r.isPartner(it!!) }.ifEmpty { availableTargets }.random()!!
                if (isRobot && card.canLock() && Random.nextBoolean())
                    lockedPlayers.add(target)
            } else {
                target =
                    if (dir == direction.Left) r.getNextLeftAlivePlayer()
                    else r.getNextRightAlivePlayer()
                if (isRobot && card.canLock())
                    lockedPlayers.add(availableTargets.random()!!)
            }
            r.game!!.resolve(OnSendCard(fsm.player, card, dir, target, lockedPlayers.toTypedArray()))
        }

        private val aiSkillMainPhase = hashMapOf<SkillId, BiPredicate<MainPhaseIdle, ActiveSkill>>(
            Pair(SkillId.XIN_SI_CHAO, BiPredicate { e, skill -> XinSiChao.ai(e, skill) }),
            Pair(SkillId.GUI_ZHA, BiPredicate { e, skill -> GuiZha.ai(e, skill) }),
            Pair(SkillId.JIAO_JI, BiPredicate { e, skill -> JiaoJi.ai(e, skill) }),
            Pair(SkillId.JIN_BI, BiPredicate { e, skill -> JinBi.ai(e, skill) }),
            Pair(SkillId.JI_BAN, BiPredicate { e, skill -> JiBan.ai(e, skill) }),
            Pair(SkillId.BO_AI, BiPredicate { e, skill -> BoAi.ai(e, skill) }),
        )
        private val aiSkillFightPhase = hashMapOf<SkillId, BiPredicate<FightPhaseIdle, ActiveSkill>>(
            Pair(SkillId.TOU_TIAN, BiPredicate { e, skill -> TouTian.ai(e, skill) }),
            Pair(SkillId.JI_ZHI, BiPredicate { e, skill -> JiZhi.ai(e, skill) }),
            Pair(SkillId.YI_HUA_JIE_MU, BiPredicate { e, skill -> YiHuaJieMu.ai(e, skill) }),
            Pair(SkillId.JIE_DAO_SHA_REN, BiPredicate { e, skill -> JieDaoShaRen.ai(e, skill) }),
            Pair(SkillId.GUANG_FA_BAO, BiPredicate { e, skill -> GuangFaBao.ai(e, skill) }),
            Pair(SkillId.JI_SONG, BiPredicate { e, skill -> JiSong.ai(e, skill) }),
            Pair(SkillId.MIAO_BI_QIAO_BIAN, BiPredicate { e, skill -> MiaoBiQiaoBian.ai(e, skill) }),
            Pair(SkillId.JIN_KOU_YI_KAI, BiPredicate { e, skill -> JinKouYiKai.ai(e, skill) }),
            Pair(SkillId.MIAO_SHOU, BiPredicate { e, skill -> MiaoShou.ai(e, skill) }),
            Pair(SkillId.SOU_JI, BiPredicate { e, skill -> SouJi.ai(e, skill) }),
            Pair(SkillId.DUI_ZHENG_XIA_YAO, BiPredicate { e, skill -> DuiZhengXiaYao.ai(e, skill) }),
            Pair(SkillId.DU_JI, BiPredicate { e, skill -> DuJi.ai(e, skill) }),
        )
        private val aiSkillReceivePhase = hashMapOf<SkillId, Predicate<Fsm>>(
            Pair(SkillId.JIN_SHEN, Predicate { fsm -> JinShen.ai(fsm) }),
            Pair(SkillId.LIAN_MIN, Predicate { fsm -> LianMin.ai(fsm) }),
            Pair(SkillId.MIAN_LI_CANG_ZHEN, Predicate { fsm -> MianLiCangZhen.ai(fsm) }),
            Pair(SkillId.QI_HUO_KE_JU, Predicate { fsm -> QiHuoKeJu.ai(fsm) }),
            Pair(SkillId.YI_YA_HUAN_YA, Predicate { fsm -> YiYaHuanYa.ai(fsm) }),
            Pair(SkillId.JING_MENG, Predicate { fsm -> JingMeng.ai(fsm) }),
            Pair(SkillId.JIAN_REN, Predicate { fsm -> JianRen.ai(fsm) }),
        )
        private val aiMainPhase = hashMapOf<card_type, BiPredicate<MainPhaseIdle, Card>>(
            Pair(card_type.Cheng_Qing, BiPredicate { e, card -> ChengQing.ai(e, card) }),
            Pair(card_type.Li_You, BiPredicate { e, card -> LiYou.ai(e, card) }),
            Pair(card_type.Ping_Heng, BiPredicate { e, card -> PingHeng.ai(e, card) }),
            Pair(card_type.Shi_Tan, BiPredicate { e, card -> ShiTan.ai(e, card) }),
            Pair(card_type.Wei_Bi, BiPredicate { e, card -> WeiBi.ai(e, card) }),
            Pair(card_type.Feng_Yun_Bian_Huan, BiPredicate { e, card -> FengYunBianHuan.ai(e, card) }),
        )
        private val aiSendPhase = hashMapOf<card_type, BiPredicate<SendPhaseIdle, Card>>(
            Pair(card_type.Po_Yi, BiPredicate { e, card -> PoYi.ai(e, card) }),
        )
        private val aiFightPhase = hashMapOf<card_type, BiPredicate<FightPhaseIdle, Card>>(
            Pair(card_type.Diao_Bao, BiPredicate { e, card -> DiaoBao.ai(e, card) }),
            Pair(card_type.Jie_Huo, BiPredicate { e, card -> JieHuo.ai(e, card) }),
            Pair(card_type.Wu_Dao, BiPredicate { e, card -> WuDao.ai(e, card) }),
        )
    }
}