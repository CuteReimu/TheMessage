package com.fengsheng.skill

import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.role

class RoleSkillsData(
    val name: String, // 角色名
    val role: role, // 角色对应协议中的枚举
    private val female: Boolean, // 是否是女性角色
    var isFaceUp: Boolean,// 角色是否面朝上
    vararg var skills: Skill // 角色技能
) {
    /**
     * 新建一个名字为“无角色”、没技能的隐藏角色
     */
    constructor() : this("无角色", Common.role.unknown, false, false)

    fun copy(): RoleSkillsData {
        return RoleSkillsData(name, role, female, isFaceUp, *skills.copyOf())
    }

    val isFemale: Boolean
        get() {
            return isFaceUp && female
        }
}