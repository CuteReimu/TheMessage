package com.fengsheng

import com.fengsheng.card.*
import com.fengsheng.phase.*
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.direction.Left
import com.fengsheng.protos.Common.direction.Right
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.notify_die_give_card_toc
import com.fengsheng.skill.*
import com.fengsheng.skill.SkillId.*
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import java.util.function.BiPredicate
import java.util.function.Predicate

class RobotPlayer : Player() {
    override fun notifyAddHandCard(location: Int, unknownCount: Int, cards: List<Card>) {
        // Do nothing
    }

    override fun notifyDrawPhase() {
        // Do nothing
    }

    override fun notifyMainPhase(waitSecond: Int) {
        val fsm = game!!.fsm as MainPhaseIdle
        if (this !== fsm.whoseTurn) return
        for (skill in skills) {
            val ai = aiSkillMainPhase[skill.skillId]
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        if (cards.size > 1 && findSkill(JI_SONG) == null && (findSkill(GUANG_FA_BAO) == null || roleFaceUp)) {
            for (card in cards.sortCards(identity)) {
                val ai = aiMainPhase[card.type]
                if (ai != null && ai.test(fsm, card)) return
            }
        }
        GameExecutor.post(game!!, { game!!.resolve(SendPhaseStart(this)) }, 1, TimeUnit.SECONDS)
    }

    override fun notifySendPhaseStart(waitSecond: Int) {
        if (waitSecond == 0) return
        val fsm = game!!.fsm as SendPhaseStart
        if (this !== fsm.whoseTurn) return
        for (skill in skills) {
            val ai = aiSkillSendPhaseStart[skill.skillId]
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        for (card in cards.sortCards(identity)) {
            val ai = aiSendPhaseStart[card.type]
            if (ai != null && ai.test(fsm, card)) return
        }
        GameExecutor.post(game!!, {
            val result = calSendMessageCard()
            game!!.resolve(OnSendCard(this, this, result.card, result.dir, result.target, result.lockedPlayers))
        }, 1, TimeUnit.SECONDS)
    }

    override fun notifySendMessageCard(
        whoseTurn: Player,
        sender: Player,
        targetPlayer: Player,
        lockedPlayers: List<Player>,
        messageCard: Card,
        dir: direction?
    ) {
        // Do nothing
    }

    override fun notifySendPhase(waitSecond: Int) {
        // Do nothing
    }

    override fun startSendPhaseTimer(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseIdle
        for (card in cards) {
            val ai = aiSendPhase[card.type]
            if (ai != null && ai.test(fsm, card)) return
        }
        GameExecutor.post(game!!, {
            val receive = fsm.mustReceiveMessage() || // 如果必须接收，则接收
                    !fsm.cannotReceiveMessage() && // 如果不能接收，则不接收
                    run {
                        val oldValue = calculateMessageCardValue(fsm.whoseTurn, this, fsm.messageCard)
                        var newValue =
                            when (fsm.dir) {
                                Left -> {
                                    val left = fsm.inFrontOfWhom.getNextLeftAlivePlayer()
                                    calculateMessageCardValue(fsm.whoseTurn, left, fsm.messageCard)
                                }

                                Right -> {
                                    val right = fsm.inFrontOfWhom.getNextRightAlivePlayer()
                                    calculateMessageCardValue(fsm.whoseTurn, right, fsm.messageCard)
                                }

                                else -> {
                                    calculateMessageCardValue(fsm.whoseTurn, fsm.sender, fsm.messageCard)
                                }
                            }
                        newValue = (newValue * 10 + calculateMessageCardValue(
                            fsm.whoseTurn,
                            fsm.lockedPlayers.ifEmpty { listOf(fsm.sender) }.first(),
                            fsm.messageCard
                        )) / 11
                        newValue <= oldValue
                    }
            game!!.resolve(
                if (receive)
                    OnChooseReceiveCard(
                        fsm.whoseTurn,
                        fsm.sender,
                        fsm.messageCard,
                        fsm.inFrontOfWhom,
                        fsm.isMessageCardFaceUp
                    )
                else
                    MessageMoveNext(fsm)
            )
        }, 1, TimeUnit.SECONDS)
    }

    override fun notifyChooseReceiveCard(player: Player) {
        // Do nothing
    }

    override fun notifyFightPhase(waitSecond: Int) {
        val fsm = game!!.fsm as FightPhaseIdle
        if (this !== fsm.whoseFightTurn) return
        for (card in cards.sortCards(identity)) {
            for (cardType in listOf(Jie_Huo, Wu_Dao, Diao_Bao)) {
                val (ok, _) = canUseCardTypes(cardType, card)
                if (ok) {
                    val ai = aiFightPhase[cardType]
                    if (ai != null && ai.test(fsm, card)) return
                }
            }
        }
        for (skill in skills) {
            val ai = aiSkillFightPhase[skill.skillId]
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        GameExecutor.post(game!!, { game!!.resolve(FightPhaseNext(fsm)) }, 1, TimeUnit.SECONDS)
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
        }, 1, TimeUnit.SECONDS)
    }

    override fun notifyWin(
        declareWinners: List<Player>,
        winners: List<Player>,
        addScoreMap: HashMap<String, Int>,
        newScoreMap: HashMap<String, Int>
    ) {
        // Do nothing
    }

    override fun notifyAskForChengQing(whoseTurn: Player, whoDie: Player, askWhom: Player, waitSecond: Int) {
        val fsm = game!!.fsm as WaitForChengQing
        if (askWhom !== this) return
        for (skill in skills) {
            val ai = aiSkillWaitForChengQing[skill.skillId]
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        run {
            var save = isPartnerOrSelf(whoDie)
            var notSave = false
            val killer = game!!.players.find { it!!.alive && it.identity == Black && it.secretTask == Killer }
            val pioneer = game!!.players.find { it!!.alive && it.identity == Black && it.secretTask == Pioneer }
            val sweeper = game!!.players.find { it!!.alive && it.identity == Black && it.secretTask == Sweeper }
            if (killer === whoseTurn && whoDie.messageCards.countTrueCard() >= 2) {
                if (killer === this) notSave = true
                save = save || killer !== this
            }
            if (pioneer === whoDie && whoDie.messageCards.countTrueCard() >= 1) {
                if (pioneer === this) notSave = true
                save = save || pioneer !== this
            }
            if (sweeper != null && whoDie.messageCards.run { count(Red) <= 1 && count(Blue) <= 1 }) {
                if (sweeper === this) notSave = true
                save = save || sweeper !== this
            }
            !notSave && save || return@run
            val card = cards.find { it is ChengQing } ?: return@run
            val black = whoDie.messageCards.filter { Black in it.colors }.run run1@{
                if (whoDie.identity == Black) {
                    when (whoDie.secretTask) {
                        Killer, Pioneer -> return@run1 find { it.colors.size > 1 } ?: firstOrNull()
                        Sweeper -> return@run1 find { it.colors.size == 1 } ?: firstOrNull()
                        else -> {}
                    }
                }
                find { it.colors.size == 1 } ?: find { identity !in it.colors } ?: firstOrNull()
            } ?: return@run
            GameExecutor.post(game!!, { card.execute(game!!, this, whoDie, black.id) }, 3, TimeUnit.SECONDS)
            return
        }
        GameExecutor.post(game!!, { game!!.resolve(WaitNextForChengQing(fsm)) }, 1, TimeUnit.SECONDS)
    }

    override fun waitForDieGiveCard(whoDie: Player, waitSecond: Int) {
        val fsm = game!!.fsm as WaitForDieGiveCard
        if (whoDie !== this) return
        GameExecutor.post(game!!, {
            if (identity != Black) {
                val target = game!!.players.find { it !== this && it!!.alive && it.identity == identity }
                if (target != null) {
                    val giveCards = cards.sortCards(identity, true).takeLast(3)
                    if (giveCards.isNotEmpty()) {
                        cards.removeAll(giveCards.toSet())
                        target.cards.addAll(giveCards)
                        game!!.addEvent(GiveCardEvent(fsm.whoseTurn, this, target))
                        logger.info("${this}给了${target}${giveCards.joinToString()}")
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
        }, 3, TimeUnit.SECONDS)
    }

    companion object {
        private val aiSkillMainPhase = hashMapOf<SkillId, BiPredicate<MainPhaseIdle, ActiveSkill>>(
            XIN_SI_CHAO to BiPredicate { e, skill -> XinSiChao.ai(e, skill) },
            GUI_ZHA to BiPredicate { e, skill -> GuiZha.ai(e, skill) },
            JIAO_JI to BiPredicate { e, skill -> JiaoJi.ai(e, skill) },
            JIN_BI to BiPredicate { e, skill -> JinBi.ai(e, skill) },
            JI_BAN to BiPredicate { e, skill -> JiBan.ai(e, skill) },
            BO_AI to BiPredicate { e, skill -> BoAi.ai(e, skill) },
            TAN_QIU_ZHEN_LI to BiPredicate { e, skill -> TanQiuZhenLi.ai(e, skill) },
            HUO_XIN to BiPredicate { e, skill -> HuoXin.ai(e, skill) },
            YUN_CHOU_WEI_WO to BiPredicate { e, skill -> YunChouWeiWo.ai(e, skill) },
            ZI_ZHENG_QING_BAI to BiPredicate { e, skill -> ZiZhengQingBai.ai(e, skill) },
            PIN_MING_SAN_LANG to BiPredicate { e, skill -> PinMingSanLang.ai(e, skill) },
            YU_SI_WANG_PO to BiPredicate { e, skill -> YuSiWangPo.ai(e, skill) },
            TAO_QU to BiPredicate { e, skill -> TaoQu.ai(e, skill) },
            TAN_XU_BIAN_SHI to BiPredicate { e, skill -> TanXuBianShi.ai(e, skill) },
            HOU_ZI_QIE_XIN to BiPredicate { e, skill -> HouZiQieXin.ai(e, skill) },
        )
        private val aiSkillSendPhaseStart = hashMapOf<SkillId, BiPredicate<SendPhaseStart, ActiveSkill>>(
            LENG_XUE_XUN_LIAN to BiPredicate { e, skill -> LengXueXunLian.ai(e, skill) },
            YOU_DI_SHEN_RU to BiPredicate { e, skill -> YouDiShenRu.ai(e, skill) },
        )
        private val aiSkillFightPhase = hashMapOf<SkillId, BiPredicate<FightPhaseIdle, ActiveSkill>>(
            TOU_TIAN to BiPredicate { e, skill -> TouTian.ai(e, skill) },
            JI_ZHI to BiPredicate { e, skill -> JiZhi.ai(e, skill) },
            YI_HUA_JIE_MU to BiPredicate { e, skill -> YiHuaJieMu.ai(e, skill) },
            JIE_DAO_SHA_REN to BiPredicate { e, skill -> JieDaoShaRen.ai(e, skill) },
            GUANG_FA_BAO to BiPredicate { e, skill -> GuangFaBao.ai(e, skill) },
            JI_SONG to BiPredicate { e, skill -> JiSong.ai(e, skill) },
            MIAO_BI_QIAO_BIAN to BiPredicate { e, skill -> MiaoBiQiaoBian.ai(e, skill) },
            JIN_KOU_YI_KAI to BiPredicate { e, skill -> JinKouYiKai.ai(e, skill) },
            MIAO_SHOU to BiPredicate { e, skill -> MiaoShou.ai(e, skill) },
            SOU_JI to BiPredicate { e, skill -> SouJi.ai(e, skill) },
            DUI_ZHENG_XIA_YAO to BiPredicate { e, skill -> DuiZhengXiaYao.ai(e, skill) },
            DU_JI to BiPredicate { e, skill -> DuJi.ai(e, skill) },
            XIAN_FA_ZHI_REN to BiPredicate { e, skill -> XianFaZhiRen.ai(e, skill) },
            ZUO_YOU_FENG_YUAN to BiPredicate { e, skill -> ZuoYouFengYuan.ai(e, skill) },
            GONG_FEN to BiPredicate { e, skill -> GongFen.ai(e, skill) },
            YUN_CHOU_WEI_WO to BiPredicate { e, skill -> YunChouWeiWo.ai(e, skill) },
            RU_BI_ZHI_SHI to BiPredicate { e, skill -> RuBiZhiShi.ai(e, skill) },
            DING_LUN to BiPredicate { e, skill -> DingLun.ai(e, skill) },
            YING_BIAN_ZI_RU to BiPredicate { e, skill -> YingBianZiRu.ai(e, skill) },
        )
        private val aiSkillReceivePhase = hashMapOf<SkillId, Predicate<Fsm>>(
            JIN_SHEN to Predicate { fsm -> JinShen.ai(fsm) },
            LIAN_MIN to Predicate { fsm -> LianMin.ai(fsm) },
            MIAN_LI_CANG_ZHEN to Predicate { fsm -> MianLiCangZhen.ai(fsm) },
            QI_HUO_KE_JU to Predicate { fsm -> QiHuoKeJu.ai(fsm) },
            YI_YA_HUAN_YA to Predicate { fsm -> YiYaHuanYa.ai(fsm) },
            JING_MENG to Predicate { fsm -> JingMeng.ai(fsm) },
            JIAN_REN to Predicate { fsm -> JianRen.ai(fsm) },
            CHI_ZI_ZHI_XIN to Predicate { fsm -> ChiZiZhiXin.ai(fsm) },
            LIAN_XIN to Predicate { fsm -> LianXin.ai(fsm) },
            MI_XIN to Predicate { fsm -> MiXin.ai(fsm) },
            JIAN_DI_FENG_XING to Predicate { fsm -> JianDiFengXing.ai(fsm) },
        )
        private val aiSkillWaitForChengQing = hashMapOf<SkillId, BiPredicate<WaitForChengQing, ActiveSkill>>(
            JI_ZHI to BiPredicate { e, skill -> JiZhi.ai2(e, skill) },
            HOU_LAI_REN to BiPredicate { e, skill -> HouLaiRen.ai(e, skill) },
        )
        private val aiMainPhase = hashMapOf<card_type, BiPredicate<MainPhaseIdle, Card>>(
            Cheng_Qing to BiPredicate { e, card -> ChengQing.ai(e, card) },
            Li_You to BiPredicate { e, card -> LiYou.ai(e, card) },
            Ping_Heng to BiPredicate { e, card -> PingHeng.ai(e, card) },
            Shi_Tan to BiPredicate { e, card -> ShiTan.ai(e, card) },
            Wei_Bi to BiPredicate { e, card -> WeiBi.ai(e, card) },
            Feng_Yun_Bian_Huan to BiPredicate { e, card -> FengYunBianHuan.ai(e, card) },
            Diao_Hu_Li_Shan to BiPredicate { e, card -> DiaoHuLiShan.ai(e, card) },
        )
        private val aiSendPhaseStart = hashMapOf<card_type, BiPredicate<SendPhaseStart, Card>>(
            Mi_Ling to BiPredicate { e, card -> MiLing.ai(e, card) },
            Yu_Qin_Gu_Zong to BiPredicate { e, card -> YuQinGuZong.ai(e, card) },
        )
        private val aiSendPhase = hashMapOf<card_type, BiPredicate<SendPhaseIdle, Card>>(
            Po_Yi to BiPredicate { e, card -> PoYi.ai(e, card) },
        )
        private val aiFightPhase = hashMapOf<card_type, BiPredicate<FightPhaseIdle, Card>>(
            Diao_Bao to BiPredicate { e, card -> DiaoBao.ai(e, card) },
            Jie_Huo to BiPredicate { e, card -> JieHuo.ai(e, card) },
            Wu_Dao to BiPredicate { e, card -> WuDao.ai(e, card) },
        )

        val cardOrder = mapOf(
            Jie_Huo to 1,
            Wu_Dao to 2,
            Diao_Bao to 3,
            Cheng_Qing to 4,
            Wei_Bi to 5,
            Shi_Tan to 6,
            Feng_Yun_Bian_Huan to 7,
            Li_You to 8,
            Mi_Ling to 9,
            Diao_Hu_Li_Shan to 10,
            Yu_Qin_Gu_Zong to 11,
            Ping_Heng to 12,
            Po_Yi to 13,
        )

        fun Card.betterThan(card: Card) = cardOrder[type]!! < cardOrder[card.type]!!

        fun Iterable<Card>.sortCards(c: color, reverse: Boolean = false): List<Card> {
            return if (reverse) sortedBy { -cardOrder[it.type]!! * 100 + if (c in it.colors) 1 else 0 }
            else sortedBy { cardOrder[it.type]!! + if (c in it.colors) 1 else 0 }
        }

        fun Iterable<Card>.bestCard(c: color, reverse: Boolean = false): Card {
            return if (reverse) minBy { -cardOrder[it.type]!! * 100 + if (c in it.colors) 1 else 0 }
            else minBy { cardOrder[it.type]!! + if (c in it.colors) 1 else 0 }
        }
    }
}