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
        private fun autoSendMessageCard(r: Player) {
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
                        it.canLock() && if (maxEnemyColorCount == 2) it.isPureBlack() else it.isBlack()
                    }
                    if (card != null) lockedPlayers.add(twoBlackEnemy)
                }
                if (card == null) card = r.cards.find { r.identity in it.colors }
            } else {
                when (r.secretTask) {
                    secret_task.Killer, secret_task.Pioneer -> card = r.cards.find { it.isBlack() }

                    secret_task.Stealer -> {
                        arrayOf(color.Red, color.Blue).any { color ->
                            val two = players.find { it.identity == color && it.messageCards.count(color) == 2 }
                                ?: return@any false
                            card =
                                r.cards.filter { color in it.colors }.run { find { it.canLock() } ?: firstOrNull() }
                                    ?: return@any false
                            if (card!!.canLock()) lockedPlayers.add(two)
                            true
                        }
                    }

                    else -> card = r.cards.find { !it.isPureBlack() }
                }
            }
            val finalCard = card ?: r.cards.first()
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
        )
        private val aiSkillSendPhaseStart = hashMapOf<SkillId, BiPredicate<SendPhaseStart, ActiveSkill>>(
            SkillId.LENG_XUE_XUN_LIAN to BiPredicate { e, skill -> LengXueXunLian.ai(e, skill) }
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
        )
        private val aiSkillWaitForChengQing = hashMapOf<SkillId, BiPredicate<WaitForChengQing, ActiveSkill>>(
            SkillId.HOU_LAI_REN to BiPredicate { e, skill -> HouLaiRen.ai(e, skill) },
        )
        private val aiMainPhase = hashMapOf<card_type, BiPredicate<MainPhaseIdle, Card>>(
            card_type.Cheng_Qing to BiPredicate { e, card -> ChengQing.ai(e, card) },
            card_type.Li_You to BiPredicate { e, card -> LiYou.ai(e, card) },
            card_type.Ping_Heng to BiPredicate { e, card -> PingHeng.ai(e, card) },
            card_type.Shi_Tan to BiPredicate { e, card -> ShiTan.ai(e, card) },
            card_type.Wei_Bi to BiPredicate { e, card -> WeiBi.ai(e, card) },
            card_type.Feng_Yun_Bian_Huan to BiPredicate { e, card -> FengYunBianHuan.ai(e, card) },
            card_type.Diao_Hu_Li_Shan to BiPredicate { e, card -> DiaoHuLiShan.ai(e, card) },
        )
        private val aiSendPhaseStart = hashMapOf<card_type, BiPredicate<SendPhaseStart, Card>>(
            card_type.Mi_Ling to BiPredicate { e, card -> MiLing.ai(e, card) },
            card_type.Yu_Qin_Gu_Zong to BiPredicate { e, card -> YuQinGuZong.ai(e, card) },
        )
        private val aiSendPhase = hashMapOf<card_type, BiPredicate<SendPhaseIdle, Card>>(
            card_type.Po_Yi to BiPredicate { e, card -> PoYi.ai(e, card) },
        )
        private val aiFightPhase = hashMapOf<card_type, BiPredicate<FightPhaseIdle, Card>>(
            card_type.Diao_Bao to BiPredicate { e, card -> DiaoBao.ai(e, card) },
            card_type.Jie_Huo to BiPredicate { e, card -> JieHuo.ai(e, card) },
            card_type.Wu_Dao to BiPredicate { e, card -> WuDao.ai(e, card) },
        )
    }
}