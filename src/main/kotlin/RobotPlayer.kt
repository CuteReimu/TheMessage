package com.fengsheng

import com.fengsheng.card.*
import com.fengsheng.phase.*
import com.fengsheng.protos.Common.card_type.*
import com.fengsheng.protos.Common.color
import com.fengsheng.protos.Common.color.Black
import com.fengsheng.protos.Common.direction
import com.fengsheng.protos.Common.direction.Left
import com.fengsheng.protos.Common.direction.Right
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.endReceivePhaseTos
import com.fengsheng.protos.notifyDieGiveCardToc
import com.fengsheng.skill.*
import com.fengsheng.skill.SkillId.*
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.TimeUnit
import kotlin.random.Random

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
            val ai = aiSkillMainPhase1[skill.skillId] ?: continue
            if (ai(fsm, skill as ActiveSkill)) return
        }
        if (!Config.IsGmEnable && game!!.players.count { it is HumanPlayer } == 1) {
            val human = game!!.players.first { it is HumanPlayer }!!
            if (isEnemy(human)) { // 对于低分的新人，敌方机器人可能不出牌
                val info = Statistics.getPlayerInfo(human.playerName)
                if (info != null) {
                    val score = info.score
                    val isPowerfulPlayer = info.winCount > 0 && info.winCount * 2 >= info.gameCount
                    if (!isPowerfulPlayer && score < 60 && Random.nextInt(60) >= score) {
                        GameExecutor.post(game!!, { game!!.resolve(SendPhaseStart(this)) }, 1, TimeUnit.SECONDS)
                        return
                    }
                }
            }
        }
        if (cards.size > 1 || findSkill(LENG_XUE_XUN_LIAN) != null || cards.size == 1 && cards.first().type == Ping_Heng) {
            val cardTypes =
                if (findSkill(JI_SONG) == null && (findSkill(GUANG_FA_BAO) == null || roleFaceUp))
                    cardOrder.keys.sortedBy { cardOrder[it] }
                else listOf(Wei_Bi)
            for (cardType in cardTypes) {
                !cannotPlayCard(cardType) || continue
                for (card in cards.sortCards(identity)) {
                    val (ok, convertCardSkill) = canUseCardTypes(cardType, card)
                    ok || continue
                    val ai = aiMainPhase[card.type] ?: continue
                    if (ai(fsm, card, convertCardSkill)) return
                }
            }
        }
        for (skill in skills) {
            val ai = aiSkillMainPhase2[skill.skillId] ?: continue
            if (ai(fsm, skill as ActiveSkill)) return
        }
        GameExecutor.post(game!!, { game!!.resolve(SendPhaseStart(this)) }, 1, TimeUnit.SECONDS)
    }

    override fun notifySendPhaseStart(waitSecond: Int) {
        if (waitSecond == 0) return
        val fsm = game!!.fsm as SendPhaseStart
        if (this !== fsm.whoseTurn) return
        for (skill in skills) {
            val ai = aiSkillSendPhaseStart[skill.skillId] ?: continue
            if (ai(fsm, skill as ActiveSkill)) return
        }
        var value = Double.NEGATIVE_INFINITY
        var cb: (() -> Unit)? = null
        var cardType = Cheng_Qing // 随便写一个
        for (card in cards.sortCards(identity)) {
            cardType != card.type || continue
            cardType = card.type
            val ai = aiSendPhaseStart[card.type] ?: continue
            val result = ai(fsm, card) ?: continue
            if (result.first > value) {
                value = result.first
                cb = result.second
            }
        }
        val result = calSendMessageCard()
        if (cb == null || result.value > value) {
            GameExecutor.post(game!!, {
                game!!.resolve(OnSendCard(this, this, result.card, result.dir, result.target, result.lockedPlayers))
            }, 1, TimeUnit.SECONDS)
        } else {
            cb()
        }
    }

    override fun notifySendMessageCard(
        whoseTurn: Player,
        sender: Player,
        targetPlayer: Player,
        lockedPlayers: List<Player>,
        messageCard: Card,
        dir: direction
    ) {
        // Do nothing
    }

    override fun notifySendPhase(waitSecond: Int) {
        // Do nothing
    }

    override fun startSendPhaseTimer(waitSecond: Int) {
        val fsm = game!!.fsm as SendPhaseIdle
        if (!cannotPlayCard(Po_Yi)) {
            for (card in cards.sortCards(identity)) {
                val (ok, convertCardSkill) = canUseCardTypes(Po_Yi, card)
                ok || continue
                val ai = aiSendPhase[card.type] ?: continue
                if (ai(fsm, card, convertCardSkill)) return
            }
        }
        GameExecutor.post(game!!, {
            val receive = fsm.mustReceiveMessage() ||
                // 如果必须接收，则接收
                !fsm.cannotReceiveMessage() &&
                // 如果不能接收，则不接收
                run {
                    val oldValue =
                        calculateMessageCardValue(fsm.whoseTurn, this, fsm.messageCard, sender = fsm.sender)
                    var newValue =
                        when (fsm.dir) {
                            Left -> {
                                val left = fsm.inFrontOfWhom.getNextLeftAlivePlayer()
                                calculateMessageCardValue(fsm.whoseTurn, left, fsm.messageCard, sender = fsm.sender)
                            }

                            Right -> {
                                val right = fsm.inFrontOfWhom.getNextRightAlivePlayer()
                                calculateMessageCardValue(
                                    fsm.whoseTurn,
                                    right,
                                    fsm.messageCard,
                                    sender = fsm.sender
                                )
                            }

                            else -> {
                                calculateMessageCardValue(
                                    fsm.whoseTurn,
                                    fsm.sender,
                                    fsm.messageCard,
                                    sender = fsm.sender
                                )
                            }
                        }
                    newValue = (newValue * 10 + calculateMessageCardValue(
                        fsm.whoseTurn,
                        fsm.lockedPlayers.ifEmpty { listOf(fsm.sender) }.first(),
                        fsm.messageCard,
                        sender = fsm.sender
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
        }, 1500, TimeUnit.MILLISECONDS)
    }

    override fun notifyChooseReceiveCard(player: Player) {
        // Do nothing
    }

    override fun notifyFightPhase(waitSecond: Int) {
        val fsm = game!!.fsm as FightPhaseIdle
        this === fsm.whoseFightTurn || return
        val delay = game!!.animationDelayMs
        game!!.animationDelayMs = 0
        if (!Config.IsGmEnable && game!!.players.count { it is HumanPlayer } == 1) {
            val human = game!!.players.first { it is HumanPlayer }!!
            if (isEnemy(human)) { // 对于低分的新人，敌方机器人可能不出牌
                val info = Statistics.getPlayerInfo(human.playerName)
                if (info != null) {
                    val score = info.score
                    val isPowerfulPlayer = info.winCount > 0 && info.winCount * 2 >= info.gameCount
                    if (!isPowerfulPlayer && score < 60 && Random.nextInt(60) >= score) {
                        GameExecutor.post(game!!, { game!!.resolve(FightPhaseNext(fsm)) }, 500 + delay, TimeUnit.MILLISECONDS)
                        return
                    }
                }
            }
        }
        for (skill in skills) {
            val ai = aiSkillFightPhase1[skill.skillId] ?: continue
            if (ai(fsm, skill as? ActiveSkill)) return
        }
        if (!game!!.isEarly ||
            this === fsm.whoseTurn ||
            isPartnerOrSelf(fsm.inFrontOfWhom) &&
            fsm.inFrontOfWhom.willDie(fsm.messageCard) ||
            calculateMessageCardValue(fsm.whoseTurn, fsm.inFrontOfWhom, fsm.messageCard, sender = fsm.sender) <= -110) {
            val result = calFightPhase(fsm)
            if (result != null && result.deltaValue > 10) {
                var actualDelay = 3L
                var timeUnit = TimeUnit.SECONDS
                if (delay > 0) {
                    actualDelay = 3000L + delay
                    timeUnit = TimeUnit.MILLISECONDS
                }
                GameExecutor.post(game!!, {
                    result.convertCardSkill?.onConvert(this)
                    if (result.cardType == Wu_Dao)
                        result.card.asCard(result.cardType).execute(game!!, this, result.wuDaoTarget!!)
                    else
                        result.card.asCard(result.cardType).execute(game!!, this)
                }, actualDelay, timeUnit)
                return
            }
            for (skill in skills) {
                val ai = aiSkillFightPhase2[skill.skillId] ?: continue
                if (ai(fsm, skill as ActiveSkill)) return
            }
        }
        GameExecutor.post(game!!, { game!!.resolve(FightPhaseNext(fsm)) }, 500 + delay, TimeUnit.MILLISECONDS)
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
            val ai = aiSkillReceivePhase[skill.skillId] ?: continue
            if (ai(game!!.fsm!!)) return
        }
        GameExecutor.TimeWheel.newTimeout({
            game!!.tryContinueResolveProtocol(this, endReceivePhaseTos {})
        }, 100, TimeUnit.MILLISECONDS)
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
            val ai = aiSkillWaitForChengQing[skill.skillId] ?: continue
            if (ai(fsm, skill as ActiveSkill)) return
        }
        run {
            !cannotPlayCard(Cheng_Qing) || return@run
            wantToSave(whoseTurn, whoDie) || return@run
            val card = cards.find { it is ChengQing } ?: return@run
            val black = whoDie.messageCards.filter { it.isBlack() }.run run1@{
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
            var target: Player? = null
            if (identity != Black)
                target = game!!.players.find { it !== this && it!!.alive && it.identity == identity }
            if (target == null) // 如果没有人给，则从本回合没有出过牌的人（不包含该回合角色和情报传出者）中随机挑一个给
                target = game!!.players.filter { it !== fsm.whoseTurn && it!!.alive && !it.useCardThisTurn }.randomOrNull()
            if (target != null) {
                val giveCards = cards.sortCards(identity, true).takeLast(3)
                if (giveCards.isNotEmpty()) {
                    cards.removeAll(giveCards.toSet())
                    target.cards.addAll(giveCards)
                    game!!.addEvent(GiveCardEvent(fsm.whoseTurn, this, target))
                    logger.info("${this}给了${target}${giveCards.joinToString()}")
                    game!!.players.send { p ->
                        notifyDieGiveCardToc {
                            playerId = p.getAlternativeLocation(location)
                            targetPlayerId = p.getAlternativeLocation(target.location)
                            if (p === target) giveCards.forEach { card.add(it.toPbCard()) }
                            else unknownCardCount = giveCards.size
                        }
                    }
                }
            }
            game!!.resolve(AfterDieGiveCard(fsm))
        }, 3, TimeUnit.SECONDS)
    }

    companion object {
        private val aiSkillMainPhase1 = hashMapOf(
            XIN_SI_CHAO to XinSiChao::ai,
            GUI_ZHA to GuiZha::ai,
            JIN_BI to JinBi::ai,
            JI_BAN to JiBan::ai,
            BO_AI to BoAi::ai,
            TAN_QIU_ZHEN_LI to TanQiuZhenLi::ai,
            HUO_XIN to HuoXin::ai,
            YUN_CHOU_WEI_WO to YunChouWeiWo::ai,
            ZI_ZHENG_QING_BAI to ZiZhengQingBai::ai,
            YU_SI_WANG_PO to YuSiWangPo::ai,
            TAO_QU to TaoQu::ai,
            TAN_XU_BIAN_SHI to TanXuBianShi::ai,
            HOU_ZI_QIE_XIN to HouZiQieXin::ai,
        )
        private val aiSkillMainPhase2 = hashMapOf(
            JIAO_JI to JiaoJi::ai,
            PIN_MING_SAN_LANG to PinMingSanLang::ai,
        )
        private val aiSkillSendPhaseStart = hashMapOf(
            LENG_XUE_XUN_LIAN to LengXueXunLian::ai,
            YOU_DI_SHEN_RU to YouDiShenRu::ai,
        )
        private val aiSkillFightPhase1 = hashMapOf<SkillId, (FightPhaseIdle, ActiveSkill?) -> Boolean>(
            TOU_TIAN to { e, skill -> TouTian.ai(e, skill!!) },
            JIE_DAO_SHA_REN to { e, skill -> JieDaoShaRen.ai(e, skill!!) },
            RU_BI_ZHI_SHI to { e, skill -> RuBiZhiShi.ai(e, skill!!) },
            JIN_KOU_YI_KAI to { e, skill -> JinKouYiKai.ai(e, skill!!) },
            GUANG_FA_BAO to { e, skill -> GuangFaBao.ai(e, skill!!) },
            DING_LUN to { e, skill -> DingLun.ai(e, skill!!) },
            BI_FENG to { e, _ -> BiFeng.ai(e) },
            SOU_JI to { e, skill -> SouJi.ai(e, skill!!) },
        )
        private val aiSkillFightPhase2 = hashMapOf(
            JI_ZHI to JiZhi::ai,
            YI_HUA_JIE_MU to YiHuaJieMu::ai,
            JI_SONG to JiSong::ai,
            MIAO_BI_QIAO_BIAN to MiaoBiQiaoBian::ai,
            MIAO_SHOU to MiaoShou::ai,
            DUI_ZHENG_XIA_YAO to DuiZhengXiaYao::ai,
            DU_JI to DuJi::ai,
            XIAN_FA_ZHI_REN to XianFaZhiRen::ai,
            ZUO_YOU_FENG_YUAN to ZuoYouFengYuan::ai,
            GONG_FEN to GongFen::ai,
            YUN_CHOU_WEI_WO to YunChouWeiWo::ai,
            YING_BIAN_ZI_RU to YingBianZiRu::ai,
        )
        private val aiSkillReceivePhase = hashMapOf(
            JIN_SHEN to JinShen::ai,
            LIAN_MIN to LianMin::ai,
            MIAN_LI_CANG_ZHEN to MianLiCangZhen::ai,
            QI_HUO_KE_JU to QiHuoKeJu::ai,
            YI_YA_HUAN_YA to YiYaHuanYa::ai,
            JING_MENG to JingMeng::ai,
            JIAN_REN to JianRen::ai,
            CHI_ZI_ZHI_XIN to ChiZiZhiXin::ai,
            LIAN_XIN to LianXin::ai,
            JIAN_DI_FENG_XING to JianDiFengXing::ai,
            ZHUANG_ZHI_MAN_HUAI to ZhuangZhiManHuai::ai,
            AN_CANG_SHA_JI to AnCangShaJi::ai,
        )
        private val aiSkillWaitForChengQing = hashMapOf(
            JI_ZHI to JiZhi::ai2,
            HOU_LAI_REN to HouLaiRen::ai,
            RU_BI_ZHI_SHI to RuBiZhiShi::ai2,
        )
        private val aiMainPhase = hashMapOf(
            Cheng_Qing to ChengQing::ai,
            Li_You to LiYou::ai,
            Ping_Heng to PingHeng::ai,
            Shi_Tan to ShiTan::ai,
            Wei_Bi to WeiBi::ai,
            Feng_Yun_Bian_Huan to FengYunBianHuan::ai,
            Diao_Hu_Li_Shan to DiaoHuLiShan::ai,
        )
        private val aiSendPhaseStart = hashMapOf(
            Mi_Ling to MiLing::ai,
            Yu_Qin_Gu_Zong to YuQinGuZong::ai,
        )
        private val aiSendPhase = hashMapOf(
            Po_Yi to PoYi::ai,
        )

        val cardOrder = mapOf(
            Jie_Huo to 1,
            Wu_Dao to 2,
            Diao_Bao to 3,
            Feng_Yun_Bian_Huan to 4,
            Li_You to 5,
            Wei_Bi to 6,
            Cheng_Qing to 7,
            Mi_Ling to 8,
            Shi_Tan to 9,
            Diao_Hu_Li_Shan to 10,
            Yu_Qin_Gu_Zong to 11,
            Ping_Heng to 12,
            Po_Yi to 13,
        )

        fun Card.betterThan(card: Card) = cardOrder[type]!! < cardOrder[card.type]!!

        /**
         * 把牌排列
         *
         * @param c 对于相同的牌，会把这个颜色排在前面（无论参数`reverse`是`true`还是`false`）。一般传入想要排序的玩家的[identity]即可
         * @param reverse 为`false`时，表示把有用的牌排在前面，没用的牌排在后面。不填为`false`
         * @return 一个新的`List`，包含了排列好的牌
         */
        fun Iterable<Card>.sortCards(c: color, reverse: Boolean = false): List<Card> =
            if (reverse) sortedBy { -cardOrder[it.type]!! * 100 + if (c in it.colors) 1 else 0 }
            else sortedBy { cardOrder[it.type]!! + if (c in it.colors) 1 else 0 }

        /**
         * 获得最有（最无）价值的牌（换句话说，就是[sortCards]后的第一个）
         *
         * @param c 对于相同的牌，会优先选这个颜色（无论参数`reverse`是`true`还是`false`）。一般传入想要排序的玩家的[identity]即可
         * @param reverse 为`false`时，表示最有价值的牌。不填为`false`
         * @return 最有（最无）价值的牌
         * @throws NoSuchElementException 如果列表为空
         */
        fun Iterable<Card>.bestCard(c: color, reverse: Boolean = false): Card =
            if (reverse) minBy { -cardOrder[it.type]!! * 100 + if (c in it.colors) 1 else 0 }
            else minBy { cardOrder[it.type]!! + if (c in it.colors) 1 else 0 }

        /**
         * 获得最有（最无）价值的牌（换句话说，就是[sortCards]后的第一个）
         *
         * @param c 对于相同的牌，会优先选这个颜色（无论参数`reverse`是`true`还是`false`）。一般传入想要排序的玩家的[identity]即可
         * @param reverse 为`false`时，表示最有价值的牌。不填为`false`
         * @return 最有（最无）价值的牌，如果列表为空，则返回`null`
         */
        fun Iterable<Card>.bestCardOrNull(c: color, reverse: Boolean = false): Card? =
            if (reverse) minByOrNull { -cardOrder[it.type]!! * 100 + if (c in it.colors) 1 else 0 }
            else minByOrNull { cardOrder[it.type]!! + if (c in it.colors) 1 else 0 }
    }
}
