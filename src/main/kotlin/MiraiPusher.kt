package com.fengsheng

import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.log4j.Logger
import java.time.Duration

object MiraiPusher {
    private val mu = Mutex()
    private val notifyQueueOnStart = HashSet<Long>()
    private val notifyQueueOnEnd = HashSet<Long>()

    fun addIntoNotifyQueue(qq: Long, onStart: Boolean) = runBlocking {
        mu.withLock {
            val map = if (onStart) notifyQueueOnStart else notifyQueueOnEnd
            map.size < 5 || return@withLock false
            map.add(qq)
            true
        }
    }

    fun notifyStart() {
        val at = runBlocking {
            mu.withLock {
                notifyQueueOnStart.toLongArray().apply { notifyQueueOnStart.clear() }
            }
        }
        if (at.isNotEmpty()) {
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                try {
                    val session = verify()
                    bind(session)
                    Config.PushQQGroups.forEach { sendGroupMessage(session, it, "开了", *at) }
                    release(session)
                } catch (e: Throwable) {
                    log.error("catch throwable", e)
                }
            }
        }
    }

    fun push(
        game: Game,
        declareWinners: List<Player>,
        winners: List<Player>,
        addScoreMap: HashMap<String, Int>,
        newScoreMap: HashMap<String, Int>
    ) {
        if (!Config.EnablePush) return
        val lines = ArrayList<String>()
        lines.add("对局结果")
        for (player in game.players.sortedBy { it!!.identity.number }) {
            val name = player!!.playerName
            val roleName = player.roleName
            val identity = Player.identityColorToString(player.identity, player.secretTask)
            val result =
                if (declareWinners.any { it === player }) "宣胜"
                else if (winners.any { it === player }) "胜利"
                else "失败"
            val addScore = addScoreMap[name] ?: 0
            val newScore = newScoreMap[name] ?: 0
            val addScoreStr =
                if (addScore > 0) "+$addScore"
                else if (addScore < 0) addScore.toString()
                else if (result == "失败") "-0"
                else "+0"
            val rank = ScoreFactory.getRankNameByScore(newScore)
            lines.add("$name,$roleName,$identity,$result,$rank,$newScore($addScoreStr)")
        }
        val text = lines.joinToString(separator = "\n")
        val at = runBlocking {
            mu.withLock {
                notifyQueueOnEnd.toLongArray().apply { notifyQueueOnEnd.clear() }
            }
        }
        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            try {
                val session = verify()
                bind(session)
                Config.PushQQGroups.forEach { sendGroupMessage(session, it, text, *at) }
                release(session)
            } catch (e: Throwable) {
                log.error("catch throwable", e)
            }
        }
    }

    private fun verify(): String {
        val postData = """{"verifyKey":"${Config.MiraiVerifyKey}"}""".toRequestBody(contentType)
        val request = Request.Builder().url("${Config.MiraiHttpUrl}/verify").post(postData).build()
        val resp = client.newCall(request).execute()
        val json = gson.fromJson(resp.body!!.string(), JsonElement::class.java)
        val code = json.asJsonObject["code"].asInt
        if (code != 0) throw Exception("verify failed: $code")
        return json.asJsonObject["session"].asString
    }

    private fun bind(sessionKey: String) {
        val postData = """{"sessionKey":"$sessionKey","qq":${Config.RobotQQ}}""".toRequestBody(contentType)
        val request = Request.Builder().url("${Config.MiraiHttpUrl}/bind").post(postData).build()
        val resp = client.newCall(request).execute()
        val json = gson.fromJson(resp.body!!.string(), JsonElement::class.java)
        val code = json.asJsonObject["code"].asInt
        if (code != 0) throw Exception("bind failed: $code")
    }

    private fun sendGroupMessage(sessionKey: String, groupId: Long, message: String, vararg at: Long) {
        val atStr = at.joinToString(separator = "") { "{\"type\":\"At\",\"target\":$it}," }
        val postData = """{
            "sessionKey":"$sessionKey",
            "target":$groupId,
            "messageChain":[$atStr{"type":"Plain","text":"$message"}]
        }""".trimMargin().toRequestBody(contentType)
        val request = Request.Builder().url("${Config.MiraiHttpUrl}/sendGroupMessage").post(postData).build()
        val resp = client.newCall(request).execute()
        val json = gson.fromJson(resp.body!!.string(), JsonElement::class.java)
        val code = json.asJsonObject["code"].asInt
        if (code != 0) throw Exception("sendGroupMessage failed: $code")
    }

    private fun release(sessionKey: String) {
        val postData = """{"sessionKey":"$sessionKey","qq":${Config.RobotQQ}}""".toRequestBody(contentType)
        val request = Request.Builder().url("${Config.MiraiHttpUrl}/release").post(postData).build()
        val resp = client.newCall(request).execute()
        val json = gson.fromJson(resp.body!!.string(), JsonElement::class.java)
        val code = json.asJsonObject["code"].asInt
        if (code != 0) throw Exception("release failed: $code")
    }

    private val client = OkHttpClient().newBuilder().connectTimeout(Duration.ofMillis(20000)).build()
    private val contentType = "application/json; charset=utf-8".toMediaTypeOrNull()
    private val gson = Gson()
    private val log = Logger.getLogger(MiraiPusher::class.java)
}