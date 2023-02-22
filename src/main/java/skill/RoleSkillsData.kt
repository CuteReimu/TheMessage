package com.fengsheng.skill

import com.fengsheng.protos.Common
import com.fengsheng.protos.Common.role
import java.util.*

class RoleSkillsData(
    /**
     * 获取角色名
     */
    val name: String,
    /**
     * 获取角色对应协议中的枚举
     */
    val role: role, private val isFemale: Boolean,
    /**
     * 设置角色是否面朝上
     */
    var isFaceUp: Boolean, vararg skills: Skill
) {

    /**
     * 获取角色是否面朝上
     */
    /**
     * 获取角色技能
     */
    /**
     * 设置角色技能
     */
    var skills: Array<Skill>

    /**
     * 新建一个名字为“无角色”、没技能的隐藏角色
     */
    constructor() : this("无角色", Common.role.unknown, false, false)

    /**
     * 角色技能数据
     *
     * @param name   角色名
     * @param role   角色对应协议中的枚举
     * @param faceUp 角色是否面朝上
     * @param skills 角色技能
     */
    init {
        this.skills = Arrays.copyOf(skills, skills.size)
    }

    constructor(data: RoleSkillsData) : this(data.name, data.role, data.isFemale, data.isFaceUp, *data.skills)

    /**
     * 获取角色是否是女性角色
     */
    fun isFemale(): Boolean {
        return isFaceUp && isFemale
    }
}