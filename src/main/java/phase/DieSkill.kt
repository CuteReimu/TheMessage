package com.fengsheng.phase

import com.fengsheng.*
import com.fengsheng.protos.Common.cardimport

com.fengsheng.skill.SkillId
/**
 * 死亡时的技能结算
 */
class DieSkill(
    /**
     * 谁的回合
     */
    var whoseTurn: Player,
    /**
     * 死亡的顺序
     */
    var diedQueue: List<Player?>,
    /**
     * 正在询问谁
     */
    var askWhom: Player,
    /**
     * 死亡结算后的下一个动作
     */
    var afterDieResolve: Fsm?
) : Fsm {
    /**
     * 结算到dieQueue的第几个人的死亡事件了
     */
    var diedIndex = 0

    /**
     * 在结算死亡技能时，又有新的人获得三张黑色情报的顺序
     */
    var receiveOrder = ReceiveOrder()
    override fun resolve(): ResolveResult? {
        if (askWhom !== diedQueue[diedIndex] && !askWhom.isAlive) return ResolveResult(DieSkillNext(this), true)
        val result = askWhom.game.dealListeningSkill()
        return result ?: ResolveResult(DieSkillNext(this), true)
    }

    /**
     * 进行下一个玩家死亡时的技能结算
     */
    private class DieSkillNext(dieSkill: DieSkill) : Fsm {
        override fun resolve(): ResolveResult? {
            dieSkill.askWhom.resetSkillUseCount(SkillId.CHENG_ZHI)
            val players = dieSkill.whoseTurn.game.players
            var askWhom = dieSkill.askWhom.location()
            while (true) {
                askWhom = (askWhom + 1) % players.size
                if (askWhom == dieSkill.whoseTurn.location()) {
                    dieSkill.diedIndex++
                    if (dieSkill.diedIndex >= dieSkill.diedQueue.size) return ResolveResult(
                        WaitForDieGiveCard(
                            dieSkill.whoseTurn,
                            dieSkill.diedQueue,
                            dieSkill.receiveOrder,
                            dieSkill.afterDieResolve
                        ), true
                    )
                    dieSkill.askWhom = dieSkill.whoseTurn
                    return ResolveResult(dieSkill, true)
                }
                if (players[askWhom] === dieSkill.diedQueue[dieSkill.diedIndex] || players[askWhom].isAlive) {
                    dieSkill.askWhom = players[askWhom]
                    return ResolveResult(dieSkill, true)
                }
            }
        }

        val dieSkill: DieSkill

        init {
            this.card = card
            this.sendPhase = sendPhase
            this.r = r
            this.target = target
            this.card = card
            this.wantType = wantType
            this.r = r
            this.target = target
            this.card = card
            this.player = player
            this.card = card
            this.card = card
            this.drawCards = drawCards
            this.players = players
            this.mainPhaseIdle = mainPhaseIdle
            this.dieSkill = dieSkill
        }
    }
}