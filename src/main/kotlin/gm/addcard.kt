package com.fengsheng.gm

import java.util.concurrent.*
import java.util.function.Function

com.fengsheng.GameExecutorimport com.fengsheng.protos.Common.card_typeimport java.util.concurrent.*import java.util.function.Function

com.fengsheng.card.*
import io.netty.util.HashedWheelTimerimport

org.apache.log4j.Loggerimport java.util.*import java.util.concurrent.*
import java.util.function.Function

class addcard : Function<Map<String?, String>, String> {
    override fun apply(form: Map<String?, String>): String {
        return try {
            val playerId = if (form.containsKey("player")) form["player"]!!.toInt() else 0
            val cardTypeNum = form["card"]!!.toInt()
            val cardType = card_type.forNumber(cardTypeNum)
            if (cardType == null || cardType == card_type.UNRECOGNIZED) return "{\"error\": \"invalid arguments\"}"
            val count = form["count"]
            val finalCount = if (count != null) Math.max(1, Math.min(count.toInt(), 99)) else 1
            val availableCards: MutableList<Card?> = ArrayList()
            for (c in Deck.Companion.DefaultDeck) if (c.getType() == cardType) availableCards.add(c)
            for (g in Game.Companion.GameCache.values) {
                GameExecutor.Companion.post(g, Runnable {
                    if (playerId < g.players.size && playerId >= 0 && g.players[playerId].isAlive) {
                        val cardList: MutableList<Card> = ArrayList()
                        for (i in 0 until finalCount) {
                            val c = availableCards[ThreadLocalRandom.current().nextInt(availableCards.size)]
                            cardList.add(
                                when (cardType) {
                                    card_type.Cheng_Qing -> ChengQing(g.deck.nextId, c)
                                    card_type.Shi_Tan -> ShiTan(g.deck.nextId, c as ShiTan?)
                                    card_type.Wei_Bi -> WeiBi(g.deck.nextId, c)
                                    card_type.Li_You -> LiYou(g.deck.nextId, c)
                                    card_type.Ping_Heng -> PingHeng(g.deck.nextId, c)
                                    card_type.Po_Yi -> PoYi(g.deck.nextId, c)
                                    card_type.Jie_Huo -> JieHuo(g.deck.nextId, c)
                                    card_type.Diao_Bao -> DiaoBao(g.deck.nextId, c)
                                    card_type.Wu_Dao -> WuDao(g.deck.nextId, c)
                                    card_type.Feng_Yun_Bian_Huan -> FengYunBianHuan(g.deck.nextId, c)
                                    else -> throw IllegalStateException("Unexpected value: $cardType")
                                }
                            )
                        }
                        val p = g.players[playerId]
                        val cards = cardList.toTypedArray()
                        p.addCard(*cards)
                        log.info("由于GM命令，" + p + "摸了" + Arrays.toString(cards) + "，现在有" + p.cards.size + "张手牌")
                        for (player in g.players) {
                            if (player.location() == playerId) player.notifyAddHandCard(
                                playerId,
                                0,
                                *cards
                            ) else player.notifyAddHandCard(playerId, cards.size)
                        }
                    }
                })
            }
            "{\"msg\": \"success\"}"
        } catch (e: NumberFormatException) {
            "{\"error\": \"invalid arguments\"}"
        } catch (e: NullPointerException) {
            "{\"error\": \"invalid arguments\"}"
        }
    }

    companion object {
        private val log = Logger.getLogger(addcard::class.java)
    }
}