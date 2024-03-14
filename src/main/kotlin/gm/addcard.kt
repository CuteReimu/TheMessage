package com.fengsheng.gm

import com.fengsheng.Game
import com.fengsheng.GameExecutor
import com.fengsheng.card.*
import com.fengsheng.phase.WaitForSelectRole
import com.fengsheng.protos.Common.card_type
import com.fengsheng.protos.Common.card_type.*
import org.apache.logging.log4j.kotlin.logger
import java.util.function.Function

class addcard : Function<Map<String, String>, Any> {
    override fun apply(form: Map<String, String>): Any {
        return try {
            val playerId = form["player"]?.toInt() ?: 0
            val cardTypeNum = form["card"]!!.toInt()
            val cardType = card_type.forNumber(cardTypeNum)
            if (cardType == null || cardType == UNRECOGNIZED) return "{\"error\": \"参数错误\"}"
            val count = form["count"]
            val finalCount = count?.toInt()?.coerceIn(1..99) ?: 1
            val availableCards = Deck.DefaultDeck.filter { it.type == cardType }
            for (g in Game.gameCache.values) {
                GameExecutor.post(g) {
                    if (!g.isStarted || g.fsm == null || g.fsm is WaitForSelectRole) return@post
                    if (playerId < g.players.size && playerId >= 0 && g.players[playerId]!!.alive) {
                        val cardList = ArrayList<Card>()
                        for (i in 0..<finalCount) {
                            val c = availableCards.random()
                            cardList.add(
                                when (cardType) {
                                    Cheng_Qing -> ChengQing(g.deck.getNextId(), c)
                                    Shi_Tan -> ShiTan(g.deck.getNextId(), c as ShiTan)
                                    Wei_Bi -> WeiBi(g.deck.getNextId(), c)
                                    Li_You -> LiYou(g.deck.getNextId(), c)
                                    Ping_Heng -> PingHeng(g.deck.getNextId(), c)
                                    Po_Yi -> PoYi(g.deck.getNextId(), c)
                                    Jie_Huo -> JieHuo(g.deck.getNextId(), c)
                                    Diao_Bao -> DiaoBao(g.deck.getNextId(), c)
                                    Wu_Dao -> WuDao(g.deck.getNextId(), c)
                                    Feng_Yun_Bian_Huan -> FengYunBianHuan(g.deck.getNextId(), c)
                                    Mi_Ling -> MiLing(g.deck.getNextId(), c as MiLing)
                                    Diao_Hu_Li_Shan -> DiaoHuLiShan(g.deck.getNextId(), c)
                                    Yu_Qin_Gu_Zong -> YuQinGuZong(g.deck.getNextId(), c)
                                    else -> throw IllegalStateException("Unexpected value: $cardType")
                                }
                            )
                        }
                        val p = g.players[playerId]!!
                        p.cards.addAll(cardList)
                        logger.info("由于GM命令，${p}摸了${cardList.joinToString()}，现在有${p.cards.size}张手牌")
                        for (player in g.players) {
                            if (player!!.location == playerId)
                                player.notifyAddHandCard(playerId, 0, cardList)
                            else
                                player.notifyAddHandCard(playerId, cardList.size)
                        }
                    }
                }
            }
            "{\"msg\": \"成功\"}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"参数错误\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"参数错误\"}"
        }
    }
}