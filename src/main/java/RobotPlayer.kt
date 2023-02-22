package com.fengsheng

import com.fengsheng.*
import com.fengsheng.RobotPlayer
import com.fengsheng.card.*
import com.fengsheng.phase.*
import com.fengsheng.phase.FightPhaseIdle
import com.fengsheng.phase.SendPhaseIdle
import com.fengsheng.phase.WaitForChengQing
import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.notify_die_give_card_toc
import com.fengsheng.skill.*
import io.netty.util.Timeout
import io.netty.util.TimerTask
import org.apache.log4j.Logger
import java.util.*
import java.util.concurrent.*
import java.util.function.BiPredicate
import java.util.function.Predicate

class RobotPlayer : Player() {
    override fun notifyAddHandCard(location: Int, unknownCount: Int, vararg cards: Card) {
        // Do nothing
    }

    override fun notifyDrawPhase() {
        // Do nothing
    }

    override fun notifyMainPhase(waitSecond: Int) {
        val fsm = game.fsm as MainPhaseIdle
        if (this !== fsm.player) return
        for (skill in skills) {
            val ai: BiPredicate<MainPhaseIdle, ActiveSkill> = RobotPlayer.Companion.aiSkillMainPhase.get(skill.skillId)
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        if (cards.size > 1 && findSkill<Skill?>(SkillId.JI_SONG) == null && (findSkill<Skill?>(SkillId.GUANG_FA_BAO) == null || isRoleFaceUp)) {
            for (card in cards.values) {
                val ai: BiPredicate<MainPhaseIdle, Card> = RobotPlayer.Companion.aiMainPhase.get(card.type)
                if (ai != null && ai.test(fsm, card)) return
            }
        }
        GameExecutor.Companion.post(game, Runnable { game.resolve(SendPhaseStart(this)) }, 2, TimeUnit.SECONDS)
    }

    override fun notifySendPhaseStart(waitSecond: Int) {
        val fsm = game.fsm as SendPhaseStart
        if (this !== fsm.player) return
        GameExecutor.Companion.post(
            game,
            Runnable { RobotPlayer.Companion.autoSendMessageCard(this, true) },
            2,
            TimeUnit.SECONDS
        )
    }

    override fun notifySendMessageCard(
        player: Player,
        targetPlayer: Player,
        lockedPlayers: Array<Player>,
        messageCard: Card,
        direction: direction?
    ) {
        // Do nothing
    }

    override fun notifySendPhase(waitSecond: Int) {
        val fsm = game.fsm as SendPhaseIdle
        if (this !== fsm.inFrontOfWhom) return
        if (this !== game.jinBiPlayer) {
            for (card in cards.values) {
                val ai: BiPredicate<SendPhaseIdle, Card> = RobotPlayer.Companion.aiSendPhase.get(card.type)
                if (ai != null && ai.test(fsm, card)) return
            }
        }
        GameExecutor.Companion.post(game, Runnable {
            val colors = fsm.messageCard.colors
            val certainlyReceive = fsm.isMessageCardFaceUp && colors.size == 1 && colors[0] != color.Black
            val certainlyReject = fsm.isMessageCardFaceUp && colors.size == 1 && colors[0] == color.Black
            if (certainlyReceive || Arrays.asList(*fsm.lockedPlayers)
                    .contains(this) || fsm.whoseTurn === this || !certainlyReject && ThreadLocalRandom.current()
                    .nextInt((game.players.size - 1) * 2) == 0
            ) game.resolve(
                OnChooseReceiveCard(
                    fsm.whoseTurn,
                    fsm.messageCard,
                    fsm.inFrontOfWhom,
                    fsm.isMessageCardFaceUp
                )
            ) else game.resolve(MessageMoveNext(fsm))
        }, 2, TimeUnit.SECONDS)
    }

    override fun notifyChooseReceiveCard(player: Player) {
        // Do nothing
    }

    override fun notifyFightPhase(waitSecond: Int) {
        val fsm = game.fsm as FightPhaseIdle
        if (this !== fsm.whoseFightTurn) return
        for (skill in skills) {
            val ai: BiPredicate<FightPhaseIdle, ActiveSkill> =
                RobotPlayer.Companion.aiSkillFightPhase.get(skill.skillId)
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        for (card in cards.values) {
            var cardType: card_type? = card.type
            if (findSkill<Skill?>(SkillId.YING_BIAN) != null && cardType == Common.card_type.Jie_Huo) cardType =
                Common.card_type.Wu_Dao
            val ai: BiPredicate<FightPhaseIdle, Card> = RobotPlayer.Companion.aiFightPhase.get(cardType)
            if (ai != null && ai.test(fsm, card)) return
        }
        GameExecutor.Companion.post(game, Runnable { game.resolve(FightPhaseNext(fsm)) }, 2, TimeUnit.SECONDS)
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
            val ai: Predicate<Fsm> = RobotPlayer.Companion.aiSkillReceivePhase.get(skill.skillId)
            if (ai != null && ai.test(game.fsm)) return
        }
        GameExecutor.Companion.TimeWheel.newTimeout(TimerTask { timeout: Timeout? ->
            game.tryContinueResolveProtocol(
                this,
                Fengsheng.end_receive_phase_tos.getDefaultInstance()
            )
        }, 2, TimeUnit.SECONDS)
    }

    override fun notifyWin(declareWinners: Array<Player>, winners: Array<Player>) {
        // Do nothing
    }

    override fun notifyAskForChengQing(whoDie: Player, askWhom: Player, waitSecond: Int) {
        val fsm = game.fsm as WaitForChengQing
        if (askWhom !== this) return
        GameExecutor.Companion.post(game, Runnable { game.resolve(WaitNextForChengQing(fsm)) }, 2, TimeUnit.SECONDS)
    }

    override fun waitForDieGiveCard(whoDie: Player, waitSecond: Int) {
        val fsm = game.fsm as WaitForDieGiveCard
        if (whoDie !== this) return
        GameExecutor.Companion.post(game, Runnable {
            if (identity != color.Black) {
                for (target in game.players) {
                    if (target !== this && target.identity == identity) {
                        val giveCards: MutableList<Card> = ArrayList()
                        for (card in cards.values) {
                            giveCards.add(card)
                            if (giveCards.size >= 3) break
                        }
                        if (!giveCards.isEmpty()) {
                            val cards = giveCards.toTypedArray()
                            for (card in cards) deleteCard(card.id)
                            target.addCard(*cards)
                            RobotPlayer.Companion.log.info(this.toString() + "给了" + target + Arrays.toString(cards))
                            for (p in game.players) {
                                if (p is HumanPlayer) {
                                    val builder = notify_die_give_card_toc.newBuilder()
                                    builder.playerId = p.getAlternativeLocation(location)
                                    builder.targetPlayerId = p.getAlternativeLocation(target.location())
                                    if (p === target) {
                                        for (card in cards) builder.addCard(card.toPbCard())
                                    } else {
                                        builder.unknownCardCount = cards.size
                                    }
                                    p.send(builder.build())
                                }
                            }
                        }
                        break
                    }
                }
            }
            game.resolve(AfterDieGiveCard(fsm))
        }, 2, TimeUnit.SECONDS)
    }

    companion object {
        private val log = Logger.getLogger(RobotPlayer::class.java)

        /**
         * 随机选择一张牌作为情报传出
         *
         * @param lock 是否考虑锁定
         */
        fun autoSendMessageCard(r: Player, lock: Boolean) {
            var card: Card? = null
            for (c in r.cards.values) {
                card = c
                break
            }
            assert(card != null)
            val random: Random = ThreadLocalRandom.current()
            val fsm = r.game.fsm as SendPhaseStart
            var dir = card.getDirection()
            if (r.findSkill<Skill?>(SkillId.LIAN_LUO) != null) {
                dir = direction.forNumber(random.nextInt(3))
                assert(dir != null)
            }
            var targetLocation = 0
            val availableLocations: MutableList<Int> = ArrayList()
            var lockedPlayer: Player? = null
            for (p in r.game.players) {
                if (p !== r && p.isAlive) availableLocations.add(p.location())
            }
            if (dir != direction.Up && lock && card!!.canLock() && random.nextInt(3) != 0) {
                val player = r.game.players[availableLocations[random.nextInt(availableLocations.size)]]
                if (player.isAlive) lockedPlayer = player
            }
            when (dir) {
                direction.Up -> {
                    targetLocation = availableLocations[random.nextInt(availableLocations.size)]
                    if (lock && card!!.canLock() && random.nextBoolean()) lockedPlayer = r.game.players[targetLocation]
                }

                direction.Left -> targetLocation = r.nextLeftAlivePlayer.location()
                direction.Right -> targetLocation = r.nextRightAlivePlayer.location()
            }
            r.game.resolve(
                OnSendCard(
                    fsm.player, card, dir, r.game.players[targetLocation],
                    if (lockedPlayer == null) arrayOfNulls(0) else arrayOf(lockedPlayer)
                )
            )
        }

        private val aiSkillMainPhase = EnumMap<SkillId, BiPredicate<MainPhaseIdle, ActiveSkill>>(
            SkillId::class.java
        )
        private val aiMainPhase: EnumMap<card_type, BiPredicate<MainPhaseIdle, Card>> =
            EnumMap<card_type, BiPredicate<MainPhaseIdle, Card>>(
                card_type::class.java
            )
        private val aiSendPhase: EnumMap<card_type, BiPredicate<SendPhaseIdle, Card>> =
            EnumMap<card_type, BiPredicate<SendPhaseIdle, Card>>(
                card_type::class.java
            )
        private val aiSkillFightPhase = EnumMap<SkillId, BiPredicate<FightPhaseIdle, ActiveSkill>>(
            SkillId::class.java
        )
        private val aiFightPhase: EnumMap<card_type, BiPredicate<FightPhaseIdle, Card>> =
            EnumMap<card_type, BiPredicate<FightPhaseIdle, Card>>(
                card_type::class.java
            )
        private val aiSkillReceivePhase = EnumMap<SkillId, Predicate<Fsm>>(
            SkillId::class.java
        )

        init {
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.XIN_SI_CHAO,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> XinSiChao.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.GUI_ZHA,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> GuiZha.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.JIAO_JI,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> JiaoJi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.JIN_BI,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> JinBi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.JI_BAN,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> JiBan.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillMainPhase.put(
                SkillId.BO_AI,
                BiPredicate { e: MainPhaseIdle, skill: ActiveSkill -> BoAi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.TOU_TIAN,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> TouTian.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.JI_ZHI,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> JiZhi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.YI_HUA_JIE_MU,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> YiHuaJieMu.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.JIE_DAO_SHA_REN,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> JieDaoShaRen.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.GUANG_FA_BAO,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> GuangFaBao.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.JI_SONG,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> JiSong.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.MIAO_BI_QIAO_BIAN,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> MiaoBiQiaoBian.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.JIN_KOU_YI_KAI,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> JinKouYiKai.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.MIAO_SHOU,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> MiaoShou.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.SOU_JI,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> SouJi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.DUI_ZHENG_XIA_YAO,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> DuiZhengXiaYao.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillFightPhase.put(
                SkillId.DU_JI,
                BiPredicate { e: FightPhaseIdle, skill: ActiveSkill -> DuJi.Companion.ai(e, skill) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.JIN_SHEN,
                Predicate { fsm0: Fsm -> JinShen.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.LIAN_MIN,
                Predicate { fsm0: Fsm -> LianMin.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.MIAN_LI_CANG_ZHEN,
                Predicate { fsm0: Fsm -> MianLiCangZhen.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.QI_HUO_KE_JU,
                Predicate { fsm0: Fsm -> QiHuoKeJu.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.YI_YA_HUAN_YA,
                Predicate { fsm0: Fsm -> YiYaHuanYa.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.JING_MENG,
                Predicate { fsm0: Fsm -> JingMeng.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiSkillReceivePhase.put(
                SkillId.JIAN_REN,
                Predicate { fsm0: Fsm -> JianRen.Companion.ai(fsm0) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Cheng_Qing,
                BiPredicate { e: MainPhaseIdle, card: Card -> ChengQing.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Li_You,
                BiPredicate { e: MainPhaseIdle, card: Card -> LiYou.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Ping_Heng,
                BiPredicate { e: MainPhaseIdle, card: Card -> PingHeng.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Shi_Tan,
                BiPredicate { e: MainPhaseIdle, card: Card -> ShiTan.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Wei_Bi,
                BiPredicate { e: MainPhaseIdle, card: Card -> WeiBi.Companion.ai(e, card) })
            RobotPlayer.Companion.aiSendPhase.put(
                Common.card_type.Po_Yi,
                BiPredicate { e: SendPhaseIdle, card: Card -> PoYi.Companion.ai(e, card) })
            RobotPlayer.Companion.aiMainPhase.put(
                Common.card_type.Feng_Yun_Bian_Huan,
                BiPredicate { e: MainPhaseIdle, card: Card -> FengYunBianHuan.Companion.ai(e, card) })
            RobotPlayer.Companion.aiFightPhase.put(
                Common.card_type.Diao_Bao,
                BiPredicate { e: FightPhaseIdle, card: Card -> DiaoBao.Companion.ai(e, card) })
            RobotPlayer.Companion.aiFightPhase.put(
                Common.card_type.Jie_Huo,
                BiPredicate { e: FightPhaseIdle, card: Card -> JieHuo.Companion.ai(e, card) })
            RobotPlayer.Companion.aiFightPhase.put(
                Common.card_type.Wu_Dao,
                BiPredicate { e: FightPhaseIdle, card: Card -> WuDao.Companion.ai(e, card) })
        }
    }
}