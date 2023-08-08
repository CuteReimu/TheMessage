package com.fengsheng.skill

import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.role

class RoleSkillsData private constructor(
    val name: String, // 角色名
    val role: role, // 角色对应协议中的枚举
    private val female: Boolean, // 是否是女性角色
    val isPublicRole: Boolean,// 是否是公开角色
) {
    /**
     * 新建一个名字为“无角色”、没技能的隐藏角色
     */
    constructor() : this("无角色", Common.role.unknown, false, false)

    constructor(name: String, role: role, female: Boolean, isPublicRole: Boolean, vararg skills: Skill) :
            this(name, role, female, isPublicRole) {
        this.skills = arrayOf(*skills)
    }

    var isFaceUp = isPublicRole

    var skills: Array<Skill> = arrayOf() // 角色技能

    fun copy(): RoleSkillsData {
        // TODO: 待确认这里是否需要 *skills.copyOf()
        val roleSkillsData = RoleSkillsData(name, role, female, isPublicRole, *skills)
        roleSkillsData.isFaceUp = isFaceUp
        return roleSkillsData
    }

    val isFemale: Boolean
        get() = isFaceUp && female

    val isMale: Boolean
        get() = isFaceUp && !female
}