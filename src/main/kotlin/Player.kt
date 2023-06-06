package com.fengsheng

import com.fengsheng.card.Card
import com.fengsheng.protos.Common.*
import com.fengsheng.skill.RoleSkillsData
import com.fengsheng.skill.Skill
import com.fengsheng.skill.SkillId
import com.fengsheng.skill.TriggeredSkill
import org.apache.log4j.Logger
import kotlin.random.Random

abstract class Player protected constructor() {
    var game: Game? = null
    var playerName: String = ""
    var location = 0
    val cards = ArrayList<Card>()
    val messageCards = ArrayList<Card>()
    var identity = color.Black
    var secretTask = secret_task.Killer
    var originIdentity = color.Black
    var originSecretTask = secret_task.Killer
    var alive = true
    var lose = false

    /** 为true表示刚死，还没死透。为false表示死透了。与诱变者的胜利判定有关 */
    var dieJustNow = false

    var roleSkillsData = RoleSkillsData()
        set(value) {
            field = value.copy()
        }
    private val skillUseCount = HashMap<SkillId, Int>()

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
    abstract fun notifySendPhaseStart(waitSecond: Int)
    abstract fun notifySendMessageCard(
        whoseTurn: Player,
        sender: Player,
        targetPlayer: Player,
        lockedPlayers: Array<Player>,
        messageCard: Card,
        dir: direction?
    )

    abstract fun notifySendPhase(waitSecond: Int)
    abstract fun notifyChooseReceiveCard(player: Player)
    abstract fun notifyFightPhase(waitSecond: Int)
    abstract fun notifyReceivePhase()
    abstract fun notifyReceivePhase(
        whoseTurn: Player,
        inFrontOfWhom: Player,
        messageCard: Card,
        waitingPlayer: Player,
        waitSecond: Int
    )

    open fun init() {
        log.info("${this}的身份是${identityColorToString(identity, secretTask)}")
        for (skill in roleSkillsData.skills) {
            (skill as? TriggeredSkill)?.init(game!!)
        }
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
        log.info("${this}摸了${cards.contentToString()}，现在有${this.cards.size}张手牌")
        for (player in game!!.players) {
            if (player === this)
                player.notifyAddHandCard(location, 0, *cards)
            else
                player!!.notifyAddHandCard(location, cards.size)
        }
    }

    abstract fun notifyAddHandCard(location: Int, unknownCount: Int, vararg cards: Card)

    fun checkThreeSameMessageCard(vararg cards: Card): Boolean {
        var red = 0
        var blue = 0
        var black = 0
        for (card in cards) {
            for (c in card.colors) {
                when (c) {
                    color.Red -> red++
                    color.Blue -> blue++
                    color.Black -> black++
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
                    color.Red -> red++
                    color.Blue -> blue++
                    color.Black -> black++
                    else -> {}
                }
            }
        }
        return hasRed && red >= 3 || hasBlue && blue >= 3 || hasBlack && black >= 3
    }

    open fun notifyDying(location: Int, loseGame: Boolean) {}
    open fun notifyDie(location: Int) {
        if (this.location == location) {
            game!!.playerDiscardCard(this, *cards.toTypedArray())
            game!!.deck.discard(*messageCards.toTypedArray())
        }
    }

    abstract fun notifyWin(declareWinners: Array<Player>, winners: Array<Player>)
    abstract fun notifyAskForChengQing(whoDie: Player, askWhom: Player, waitSecond: Int)
    abstract fun waitForDieGiveCard(whoDie: Player, waitSecond: Int)

    var skills: Array<Skill>
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

    fun addSkillUseCount(skillId: SkillId) {
        addSkillUseCount(skillId, 1)
    }

    fun addSkillUseCount(skillId: SkillId, count: Int) {
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

    fun getNextLeftAlivePlayer(): Player {
        var left = location - 1
        while (left != location) {
            if (left < 0) left += game!!.players.size
            if (game!!.players[left]!!.alive) break
            left--
        }
        return game!!.players[left]!!
    }

    fun getNextRightAlivePlayer(): Player {
        var right = location + 1
        while (right != location) {
            if (right >= game!!.players.size) right -= game!!.players.size
            if (game!!.players[right]!!.alive) break
            right++
        }
        return game!!.players[right]!!
    }

    /** @return 如果other是队友（不含自己），则返回true。如果是自己，或者不是队友，则返回false */
    fun isPartner(other: Player) = this !== other && identity != color.Black && identity == other.identity

    /** @return 如果other是队友或自己，则返回true。否则返回false */
    fun isPartnerOrSelf(other: Player) = this === other || identity != color.Black && identity == other.identity

    /** @return 如果other是敌人，则返回true。否则返回false */
    fun isEnemy(other: Player) = !isPartnerOrSelf(other)

    /** 亲密度。敌对阵营是0，神秘人是1，队友是2。如果自己是神秘人，则所有人都是1。 */
    private fun getFriendship(other: Player) =
        if (identity == color.Black) 1
        else when (other.identity) {
            identity -> 2
            color.Black -> 1
            else -> 0
        }

    /**
     * 比较两个人的亲密度。
     * @return 大于0表示前者比后者更亲密
     */
    fun checkFriendship(other1: Player, other2: Player): Int = getFriendship(other1) - getFriendship(other2)

    override fun toString(): String {
        val hide = if (roleFaceUp) "" else "(隐)"
        return "${location}号[$roleName$hide]"
    }

    companion object {
        private val log = Logger.getLogger(Player::class.java)

        /**
         * （日志用）将颜色转为角色身份的字符串
         */
        fun identityColorToString(c: color): String {
            return when (c) {
                color.Red -> "红方"
                color.Blue -> "蓝方"
                color.Black -> "神秘人"
                else -> throw RuntimeException("unknown color: $c")
            }
        }

        /**
         * （日志用）将颜色转为角色身份的字符串
         */
        fun identityColorToString(c: color, task: secret_task): String {
            return when (c) {
                color.Red -> "红方"
                color.Blue -> "蓝方"
                color.Black -> when (task) {
                    secret_task.Killer -> "神秘人[镇压者]"
                    secret_task.Stealer -> "神秘人[簒夺者]"
                    secret_task.Collector -> "神秘人[双重间谍]"
                    secret_task.Mutator -> "神秘人[诱变者]"
                    secret_task.Pioneer -> "神秘人[先行者]"
                    else -> throw RuntimeException("unknown secret task: $task")
                }

                color.Has_No_Identity -> "无身份"
                else -> throw RuntimeException("unknown color: $c")
            }
        }

        fun randPlayerName(game: Game): String {
            val except = game.players.mapNotNull { it?.playerName }
            return (setOf(
                "这是机器人", "去群里喊人", "喊人一起玩", "不要单机",
                "人多才好玩", "单机没意思", "别玩人机局", "多喊点人",
                "人机没意思", "群友也想玩"
            ) - except.toSet()).randomOrNull() ?: Random.nextInt(Int.MAX_VALUE).toString()
        }
    }
}