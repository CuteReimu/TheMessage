package com.fengsheng

import com.fengsheng.card.*
import com.fengsheng.phase.*
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.direction.Left
import com.fengsheng.protos.Common.direction.Right
import com.fengsheng.protos.Fengsheng
import com.fengsheng.protos.Fengsheng.notify_die_give_card_toc
import com.fengsheng.skill.*
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
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
        val fsm = game!!.fsm as MainPhaseIdle
        if (this !== fsm.whoseTurn) return
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
        if (waitSecond == 0) return
        val fsm = game!!.fsm as SendPhaseStart
        if (this !== fsm.whoseTurn) return
        for (skill in skills) {
            val ai = aiSkillSendPhaseStart[skill.skillId]
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
        for (card in cards) {
            val ai = aiSendPhaseStart[card.type]
            if (ai != null && ai.test(fsm, card)) return
        }
        GameExecutor.post(game!!, {
            autoSendMessageCard(this)
        }, 2, TimeUnit.SECONDS)
    }

    override fun notifySendMessageCard(
        whoseTurn: Player,
        sender: Player,
        targetPlayer: Player,
        lockedPlayers: Array<Player>,
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
                        val newValue =
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
            for (cardType in listOf(Jie_Huo, Wu_Dao, Diao_Bao)) {
                val (ok, _) = canUseCardTypes(cardType, card)
                if (ok) {
                    val ai = aiFightPhase[cardType]
                    if (ai != null && ai.test(fsm, card)) return
                }
            }
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

    override fun notifyWin(
        declareWinners: List<Player>,
        winners: List<Player>,
        addScoreMap: HashMap<String, Int>,
        newScoreMap: HashMap<String, Int>
    ) {
        // Do nothing
    }

    override fun notifyAskForChengQing(whoDie: Player, askWhom: Player, waitSecond: Int) {
        val fsm = game!!.fsm as WaitForChengQing
        if (askWhom !== this) return
        for (skill in skills) {
            val ai = aiSkillWaitForChengQing[skill.skillId]
            if (ai != null && ai.test(fsm, skill as ActiveSkill)) return
        }
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
                val target = game!!.players.find { it !== this && it!!.alive && it.identity == identity }
                if (target != null) {
                    val giveCards = cards.take(3)
                    if (giveCards.isNotEmpty()) {
                        cards.removeAll(giveCards.toSet())
                        target.cards.addAll(giveCards)
                        game!!.addEvent(GiveCardEvent(fsm.whoseTurn, this, target))
                        logger.info("${this}给了${target}${giveCards.toTypedArray().contentToString()}")
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
        /**
         * 随机选择一张牌作为情报传出
         */
        private fun autoSendMessageCard(r: Player) {
            val canSendPureBlack = r.findSkill(SkillId.HAN_HOU_LAO_SHI) == null || r.cards.all { it.isPureBlack() }
            var card: Card? = null
            val lockedPlayers = ArrayList<Player>()
            val players = r.game!!.players.filterNotNull().filter { r !== it && it.alive }
            if (r.identity != color.Black) {
                val enemyColor = arrayOf(color.Red, color.Blue).first { it != r.identity }
                val twoBlackEnemy = players.find {
                    it.identity == enemyColor && it.messageCards.count(color.Black) == 2
                }
                if (twoBlackEnemy != null) {
                    val maxEnemyColorCount = players.filter { it.identity == enemyColor }
                        .maxOf { it.messageCards.count(enemyColor) }
                    card = r.cards.find {
                        (canSendPureBlack || !it.isPureBlack()) &&
                                (it.canLock() || r.findSkill(SkillId.QIANG_YING_XIA_LING) != null) &&
                                if (maxEnemyColorCount == 2) it.isPureBlack() else it.isBlack()
                    }
                    if (card != null) lockedPlayers.add(twoBlackEnemy)
                }
                if (card == null)
                    card = r.cards.find { (canSendPureBlack || !it.isPureBlack()) && r.identity in it.colors }
            } else {
                when (r.secretTask) {
                    secret_task.Killer, secret_task.Pioneer ->
                        card = r.cards.find { (canSendPureBlack || !it.isPureBlack()) && it.isBlack() }

                    secret_task.Stealer -> {
                        arrayOf(color.Red, color.Blue).any { color ->
                            val two = players.find { it.identity == color && it.messageCards.count(color) == 2 }
                                ?: return@any false
                            card = r.cards.filter {
                                (canSendPureBlack || !it.isPureBlack()) && color in it.colors
                            }.run {
                                find { it.canLock() || r.findSkill(SkillId.QIANG_YING_XIA_LING) != null }
                                    ?: firstOrNull()
                            } ?: return@any false
                            if (card!!.canLock() || r.findSkill(SkillId.QIANG_YING_XIA_LING) != null)
                                lockedPlayers.add(two)
                            true
                        }
                    }

                    else -> card = r.cards.find { (canSendPureBlack || !it.isPureBlack()) && !it.isPureBlack() }
                }
            }
            val finalCard = card ?: if (canSendPureBlack) r.cards.first() else r.cards.first { !it.isPureBlack() }
            val dir =
                if (r.findSkill(SkillId.LIAN_LUO) == null) finalCard.direction
                else if (lockedPlayers.isNotEmpty()) direction.Up
                else arrayOf(direction.Up, direction.Left, direction.Right).random()
            val target = if (dir == direction.Up) {
                lockedPlayers.firstOrNull()
                    ?: (if (!finalCard.isBlack()) players.filter { r.isPartner(it) }.randomOrNull()
                    else if (finalCard.isPureBlack()) players.filter { r.isEnemy(it) }.randomOrNull()
                    else null) ?: players.random()
            } else {
                if (dir == direction.Left) r.getNextLeftAlivePlayer()
                else r.getNextRightAlivePlayer()
            }
            r.game!!.resolve(OnSendCard(r, r, finalCard, dir, target, lockedPlayers.toTypedArray()))
        }

        private val aiSkillMainPhase = hashMapOf<SkillId, BiPredicate<MainPhaseIdle, ActiveSkill>>(
            SkillId.XIN_SI_CHAO to BiPredicate { e, skill -> XinSiChao.ai(e, skill) },
            SkillId.GUI_ZHA to BiPredicate { e, skill -> GuiZha.ai(e, skill) },
            SkillId.JIAO_JI to BiPredicate { e, skill -> JiaoJi.ai(e, skill) },
            SkillId.JIN_BI to BiPredicate { e, skill -> JinBi.ai(e, skill) },
            SkillId.JI_BAN to BiPredicate { e, skill -> JiBan.ai(e, skill) },
            SkillId.BO_AI to BiPredicate { e, skill -> BoAi.ai(e, skill) },
            SkillId.TAN_QIU_ZHEN_LI to BiPredicate { e, skill -> TanQiuZhenLi.ai(e, skill) },
            SkillId.HUO_XIN to BiPredicate { e, skill -> HuoXin.ai(e, skill) },
            SkillId.YUN_CHOU_WEI_WO to BiPredicate { e, skill -> YunChouWeiWo.ai(e, skill) },
            SkillId.ZI_ZHENG_QING_BAI to BiPredicate { e, skill -> ZiZhengQingBai.ai(e, skill) },
            SkillId.PIN_MING_SAN_LANG to BiPredicate { e, skill -> PinMingSanLang.ai(e, skill) },
            SkillId.YU_SI_WANG_PO to BiPredicate { e, skill -> YuSiWangPo.ai(e, skill) },
            SkillId.TAO_QU to BiPredicate { e, skill -> TaoQu.ai(e, skill) },
            SkillId.TAN_XU_BIAN_SHI to BiPredicate { e, skill -> TanXuBianShi.ai(e, skill) },
            SkillId.HOU_ZI_QIE_XIN to BiPredicate { e, skill -> HouZiQieXin.ai(e, skill) },
        )
        private val aiSkillSendPhaseStart = hashMapOf<SkillId, BiPredicate<SendPhaseStart, ActiveSkill>>(
            SkillId.LENG_XUE_XUN_LIAN to BiPredicate { e, skill -> LengXueXunLian.ai(e, skill) },
            SkillId.YOU_DI_SHEN_RU to BiPredicate { e, skill -> YouDiShenRu.ai(e, skill) },
        )
        private val aiSkillFightPhase = hashMapOf<SkillId, BiPredicate<FightPhaseIdle, ActiveSkill>>(
            SkillId.TOU_TIAN to BiPredicate { e, skill -> TouTian.ai(e, skill) },
            SkillId.JI_ZHI to BiPredicate { e, skill -> JiZhi.ai(e, skill) },
            SkillId.YI_HUA_JIE_MU to BiPredicate { e, skill -> YiHuaJieMu.ai(e, skill) },
            SkillId.JIE_DAO_SHA_REN to BiPredicate { e, skill -> JieDaoShaRen.ai(e, skill) },
            SkillId.GUANG_FA_BAO to BiPredicate { e, skill -> GuangFaBao.ai(e, skill) },
            SkillId.JI_SONG to BiPredicate { e, skill -> JiSong.ai(e, skill) },
            SkillId.MIAO_BI_QIAO_BIAN to BiPredicate { e, skill -> MiaoBiQiaoBian.ai(e, skill) },
            SkillId.JIN_KOU_YI_KAI to BiPredicate { e, skill -> JinKouYiKai.ai(e, skill) },
            SkillId.MIAO_SHOU to BiPredicate { e, skill -> MiaoShou.ai(e, skill) },
            SkillId.SOU_JI to BiPredicate { e, skill -> SouJi.ai(e, skill) },
            SkillId.DUI_ZHENG_XIA_YAO to BiPredicate { e, skill -> DuiZhengXiaYao.ai(e, skill) },
            SkillId.DU_JI to BiPredicate { e, skill -> DuJi.ai(e, skill) },
            SkillId.XIAN_FA_ZHI_REN to BiPredicate { e, skill -> XianFaZhiRen.ai(e, skill) },
            SkillId.ZUO_YOU_FENG_YUAN to BiPredicate { e, skill -> ZuoYouFengYuan.ai(e, skill) },
            SkillId.GONG_FEN to BiPredicate { e, skill -> GongFen.ai(e, skill) },
            SkillId.YUN_CHOU_WEI_WO to BiPredicate { e, skill -> YunChouWeiWo.ai(e, skill) },
            SkillId.RU_BI_ZHI_SHI to BiPredicate { e, skill -> RuBiZhiShi.ai(e, skill) },
            SkillId.DING_LUN to BiPredicate { e, skill -> DingLun.ai(e, skill) },
            SkillId.YING_BIAN_ZI_RU to BiPredicate { e, skill -> YingBianZiRu.ai(e, skill) },
        )
        private val aiSkillReceivePhase = hashMapOf<SkillId, Predicate<Fsm>>(
            SkillId.JIN_SHEN to Predicate { fsm -> JinShen.ai(fsm) },
            SkillId.LIAN_MIN to Predicate { fsm -> LianMin.ai(fsm) },
            SkillId.MIAN_LI_CANG_ZHEN to Predicate { fsm -> MianLiCangZhen.ai(fsm) },
            SkillId.QI_HUO_KE_JU to Predicate { fsm -> QiHuoKeJu.ai(fsm) },
            SkillId.YI_YA_HUAN_YA to Predicate { fsm -> YiYaHuanYa.ai(fsm) },
            SkillId.JING_MENG to Predicate { fsm -> JingMeng.ai(fsm) },
            SkillId.JIAN_REN to Predicate { fsm -> JianRen.ai(fsm) },
            SkillId.CHI_ZI_ZHI_XIN to Predicate { fsm -> ChiZiZhiXin.ai(fsm) },
            SkillId.LIAN_XIN to Predicate { fsm -> LianXin.ai(fsm) },
            SkillId.MI_XIN to Predicate { fsm -> MiXin.ai(fsm) },
            SkillId.JIAN_DI_FENG_XING to Predicate { fsm -> JianDiFengXing.ai(fsm) },
        )
        private val aiSkillWaitForChengQing = hashMapOf<SkillId, BiPredicate<WaitForChengQing, ActiveSkill>>(
            SkillId.HOU_LAI_REN to BiPredicate { e, skill -> HouLaiRen.ai(e, skill) },
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
    }
}