package com.fengsheng

import com.fengsheng.card.Card
import com.fengsheng.protos.Common.*
import com.fengsheng.protos.Common.color.*
import com.fengsheng.protos.Common.role.unknown
import com.fengsheng.protos.Common.secret_task.*
import com.fengsheng.protos.Errcode.error_message_toc
import com.fengsheng.skill.RoleCache
import com.fengsheng.skill.RoleSkillsData
import com.fengsheng.skill.Skill
import com.fengsheng.skill.SkillId
import com.google.protobuf.GeneratedMessage
import org.apache.logging.log4j.kotlin.logger
import kotlin.random.Random

abstract class Player protected constructor() {
    @Volatile
    var game: Game? = null
    var playerName = ""

    @Volatile
    var playerTitle = ""
    var location = 0
    val cards = ArrayList<Card>()
    val messageCards = ArrayList<Card>()
    var identity = Black
    var secretTask = Killer
    var originIdentity = Black
    var originSecretTask = Killer
    var originRole = unknown
    var alive = true
    var lose = false

    /** 为true表示刚死，还没死透。为false表示死透了。与诱变者的胜利判定有关 */
    var dieJustNow = false

    /** 是否曾经面朝上过（公开角色直接为`true`） */
    var hasEverFaceUp = false

    /**
     * 威逼不透视概率，用于机器人。开局威逼百分之百不透视，每次失败变为零，每次成功加一。
     */
    var weiBiFailRate = 1

    /** 本回合是否使用过牌 */
    var useCardThisTurn = false

    var roleSkillsData = RoleSkillsData()
        set(value) {
            field = value.copy()
            hasEverFaceUp = field.isFaceUp
        }
    private val skillUseCount = HashMap<SkillId, Int>()

    open fun reset() {
        game = null
        cards.clear()
        messageCards.clear()
        alive = true
        lose = false
        dieJustNow = false
        hasEverFaceUp = false
        skillUseCount.clear()
    }

    /**
     * 向玩家客户端发送[error_message_toc]
     */
    open fun sendErrorMessage(message: String) {}

    /**
     * 向玩家客户端发送协议
     */
    open fun send(message: GeneratedMessage) {}

    /**
     * 向玩家客户端发送协议
     */
    open fun send(protoName: String, buf: ByteArray, flush: Boolean) {}

    fun deleteCard(id: Int): Card? {
        val index = cards.indexOfFirst { c -> c.id == id }
        return if (index >= 0) cards.removeAt(index) else null
    }

    fun findCard(id: Int): Card? {
        return cards.find { c -> c.id == id }
    }

    fun deleteMessageCard(id: Int): Card? {
        val index = messageCards.indexOfFirst { c -> c.id == id }
        return if (index >= 0) messageCards.removeAt(index) else null
    }

    fun findMessageCard(id: Int): Card? {
        return messageCards.find { c -> c.id == id }
    }

    abstract fun notifyDrawPhase()
    abstract fun notifyMainPhase(waitSecond: Int)
    abstract fun notifySendPhaseStart(waitSecond: Int = 0)
    abstract fun notifySendMessageCard(
        whoseTurn: Player,
        sender: Player,
        targetPlayer: Player,
        lockedPlayers: List<Player>,
        messageCard: Card,
        dir: direction
    )

    abstract fun notifySendPhase(waitSecond: Int = 0)
    abstract fun startSendPhaseTimer(waitSecond: Int)
    abstract fun notifyChooseReceiveCard(player: Player)
    abstract fun notifyFightPhase(waitSecond: Int)
    abstract fun notifyReceivePhase()
    abstract fun notifyReceivePhase(
        whoseTurn: Player,
        inFrontOfWhom: Player,
        messageCard: Card,
        waitingPlayer: Player,
        waitSecond: Int = game!!.waitSecond
    )

    open fun init() {
        logger.info("${this}的身份是${identityColorToString(identity, secretTask)}")
    }

    open fun incrSeq() {}

    fun getAbstractLocation(location: Int): Int {
        return (location + this.location) % game!!.players.size
    }

    fun getAlternativeLocation(location: Int): Int {
        var loc = location
        loc -= this.location
        if (loc < 0) {
            loc += game!!.players.size
        }
        return loc
    }

    fun draw(n: Int) {
        val cards = game!!.deck.draw(n)
        this.cards.addAll(cards)
        logger.info("${this}摸了${cards.joinToString()}，现在有${this.cards.size}张手牌")
        for (player in game!!.players) {
            if (player === this)
                player.notifyAddHandCard(location, 0, cards)
            else
                player!!.notifyAddHandCard(location, cards.size)
        }
    }

    abstract fun notifyAddHandCard(location: Int, unknownCount: Int, cards: List<Card> = emptyList())

    fun checkThreeSameMessageCard(card: Card): Boolean {
        return checkThreeSameMessageCard(listOf(card))
    }

    fun checkThreeSameMessageCard(cards: Iterable<Card>): Boolean {
        var red = 0
        var blue = 0
        var black = 0
        for (card in cards) {
            for (c in card.colors) {
                when (c) {
                    Red -> red++
                    Blue -> blue++
                    Black -> black++
                    else -> {}
                }
            }
        }
        val hasRed = red > 0
        val hasBlue = blue > 0
        val hasBlack = black > 0
        for (card in messageCards) {
            for (c in card.colors) {
                when (c) {
                    Red -> red++
                    Blue -> blue++
                    Black -> black++
                    else -> {}
                }
            }
        }
        return hasRed && red >= 3 || hasBlue && blue >= 3 || hasBlack && black >= 3
    }

    open fun notifyDying(location: Int, loseGame: Boolean) {}
    open fun notifyDie(location: Int) {}

    abstract fun notifyWin(
        declareWinners: List<Player>,
        winners: List<Player>,
        addScoreMap: HashMap<String, Int>,
        newScoreMap: HashMap<String, Int>
    )

    abstract fun notifyAskForChengQing(whoseTurn: Player, whoDie: Player, askWhom: Player, waitSecond: Int)
    abstract fun waitForDieGiveCard(whoDie: Player, waitSecond: Int)

    var skills: List<Skill>
        get() = roleSkillsData.skills
        set(value) {
            roleSkillsData.skills = value
        }

    fun findSkill(skillId: SkillId): Skill? {
        return skills.find { skill -> skill.skillId == skillId }
    }

    val roleName: String get() = roleSkillsData.name
    val role: role get() = roleSkillsData.role
    var roleFaceUp: Boolean
        get() = roleSkillsData.isFaceUp
        set(value) {
            roleSkillsData.isFaceUp = value
        }
    val isFemale: Boolean get() = roleSkillsData.isFemale
    val isMale: Boolean get() = roleSkillsData.isMale
    val isPublicRole: Boolean get() = roleSkillsData.isPublicRole

    fun addSkillUseCount(skillId: SkillId, count: Int = 1) {
        skillUseCount.compute(skillId) { _, v -> if (v == null) count else v + count }
    }

    fun getSkillUseCount(skillId: SkillId): Int {
        return skillUseCount[skillId] ?: 0
    }

    fun resetSkillUseCount() {
        skillUseCount.clear()
    }

    /**
     * 重置每回合技能使用次数计数
     */
    fun resetSkillUseCount(skillId: SkillId) {
        skillUseCount.remove(skillId)
    }

    /**
     * @return 自己左边最近的活着的玩家，如果全死了则会返回自己
     */
    fun getNextLeftAlivePlayer(): Player {
        var left = location - 1
        if (left < 0) left += game!!.players.size
        while (left != location) {
            if (game!!.players[left]!!.alive) break
            left--
            if (left < 0) left += game!!.players.size
        }
        return game!!.players[left]!!
    }

    /**
     * @return 自己右边最近的活着的玩家，如果全死了则会返回自己
     */
    fun getNextRightAlivePlayer(): Player {
        var right = location + 1
        if (right >= game!!.players.size) right -= game!!.players.size
        while (right != location) {
            if (game!!.players[right]!!.alive) break
            right++
            if (right >= game!!.players.size) right -= game!!.players.size
        }
        return game!!.players[right]!!
    }

    /** @return 如果other是队友（不含自己），则返回true。如果是自己，或者不是队友，则返回false */
    fun isPartner(other: Player) = this !== other && identity != Black && identity == other.identity

    /** @return 如果other是队友或自己，则返回true。否则返回false */
    fun isPartnerOrSelf(other: Player) = this === other || identity != Black && identity == other.identity

    /** @return 如果other是敌人，则返回true。否则返回false */
    fun isEnemy(other: Player) = !isPartnerOrSelf(other)

    override fun toString(): String {
        val hide = if (roleFaceUp) "" else "(隐)"
        val name = this.playerName.let { if (it.isEmpty()) "" else "($it)" }
        val game = this.game?.run { "(rid=$id)" } ?: ""
        return "${location}号[$roleName$hide]$name$game"
    }

    companion object {
        /**
         * （日志用）将颜色转为角色身份的字符串
         */
        fun identityColorToString(c: color): String {
            return when (c) {
                Red -> "红方"
                Blue -> "蓝方"
                Black -> "神秘人"
                Has_No_Identity -> "无身份"
                else -> "未知身份：$c"
            }
        }

        /**
         * （日志用）将颜色转为角色身份的字符串
         */
        fun identityColorToString(c: color, task: secret_task): String {
            return when (c) {
                Red -> "红方"
                Blue -> "蓝方"
                Black -> when (task) {
                    Killer -> "神秘人[镇压者]"
                    Stealer -> "神秘人[簒夺者]"
                    Collector -> "神秘人[双面间谍]"
                    Mutator -> "神秘人[诱变者]"
                    Pioneer -> "神秘人[先行者]"
                    Disturber -> "神秘人[搅局者]"
                    Sweeper -> "神秘人[清道夫]"
                    else -> "神秘人[未知任务：$task]"
                }

                Has_No_Identity -> "无身份"
                else -> "未知身份：$c"
            }
        }

        fun randPlayerName(game: Game): String {
            val except = game.players.mapNotNull { it?.playerName }
            val part1 =
                listOf("激动", "愉悦", "喜悦", "悲伤", "欢乐", "愤怒", "恐惧", "忧虑", "开心", "感激", "失望", "放松")
                    .filter { except.all { s -> !s.startsWith("${it}的") } }.randomOrNull()
            val part2 = RoleCache.randRoleName()
                ?.replaceFirst("SP", "", true)
                ?.replaceFirst("青年", "")
                ?.replaceFirst("成年", "")
                ?.replaceFirst("间谍", "")
            if (part1 != null && part2 != null) return "${part1}的$part2"
            return (setOf(
                "这是机器人", "去群里喊人", "喊人一起玩", "不要单机",
                "人多才好玩", "单机没意思", "别玩人机局", "多喊点人",
                "人机没意思", "群友也想玩"
            ) - except.toSet()).randomOrNull() ?: Random.nextInt(Int.MAX_VALUE).toString()
        }
    }
}

inline fun Iterable<Player?>.send(message: (HumanPlayer) -> GeneratedMessage) =
    forEach { (it as? HumanPlayer)?.send(message(it)) }
