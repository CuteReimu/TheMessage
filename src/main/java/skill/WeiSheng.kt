package com.fengsheng.skill

/**
 * 顾小梦技能【尾声】：你获得胜利时，没有身份牌的玩家与你一同获得胜利。
 */
class WeiSheng : AbstractSkill() {
    override fun getSkillId(): SkillId? {
        return SkillId.WEI_SHENG
    }
}