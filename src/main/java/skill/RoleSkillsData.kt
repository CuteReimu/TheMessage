package com.fengsheng.skill

import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.role

data class RoleSkillsData(
    val name: String, // 角色名
    val role: role, // 角色对应协议中的枚举
    private val female: Boolean, // 是否是女性角色
    var isFaceUp: Boolean,// 角色是否面朝上
    var skills: Array<Skill> // 角色技能
) {
    /**
     * 新建一个名字为“无角色”、没技能的隐藏角色
     */
    constructor() : this("无角色", Common.role.unknown, false, false, arrayOf())
    
    constructor(name: String, role: role, female: Boolean, isFaceUp: Boolean, vararg skills: Skill)
            : this(name, role, female, isFaceUp, arrayOf(*skills))

    val isFemale: Boolean
        get() {
            return isFaceUp && female
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RoleSkillsData

        if (role != other.role) return false

        return true
    }

    override fun hashCode(): Int {
        return role.hashCode()
    }
}