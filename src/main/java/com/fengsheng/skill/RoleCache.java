package com.fengsheng.skill;

import com.fengsheng.protos.Common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 记录所有角色的工具类
 */
public final class RoleCache {
    private static final List<RoleSkillsData> cache = List.of();

    private static final Map<Common.role, RoleSkillsData> mapCache = new HashMap<>();

    static {
        for (RoleSkillsData data : cache)
            mapCache.put(data.getRole(), data);
    }

    private RoleCache() {

    }

    /**
     * @return 长度为 {@code n} 的数组
     */
    public static RoleSkillsData[] getRandomRoles(int n) {
        RoleSkillsData[] result = new RoleSkillsData[n];
        Random random = ThreadLocalRandom.current();
        int[] indexArray = new int[Math.max(cache.size(), n)];
        for (int i = 0; i < indexArray.length; i++)
            indexArray[i] = i;
        for (int i = 0; i < indexArray.length; i++) {
            int index = random.nextInt(i, n);
            if (i != index) {
                int temp = indexArray[i];
                indexArray[i] = indexArray[index];
                indexArray[index] = temp;
            }
        }
        for (int i = 0; i < n; i++)
            result[i] = indexArray[i] < cache.size() ? cache.get(indexArray[i]) : new RoleSkillsData();
        return result;
    }


    /**
     * @param roles 返回数组的前几个角色强行指定
     * @return 长度为 {@code n} 的数组
     */
    public static RoleSkillsData[] getRandomRolesWithSpecific(int n, Common.role... roles) {
        RoleSkillsData[] roleSkillsDataArray = getRandomRoles(n);
        for (int roleIndex = 0; roleIndex < roles.length; roleIndex++) {
            int index = -1;
            for (int i = 0; i < roleSkillsDataArray.length; i++) {
                if (roles[roleIndex] == roleSkillsDataArray[i].getRole()) {
                    index = i;
                    break;
                }
            }
            if (index == -1) {
                RoleSkillsData data = mapCache.get(roles[roleIndex]);
                roleSkillsDataArray[roleIndex] = data != null ? data : new RoleSkillsData();
            } else {
                RoleSkillsData temp = roleSkillsDataArray[index];
                roleSkillsDataArray[index] = roleSkillsDataArray[roleIndex];
                roleSkillsDataArray[roleIndex] = temp;
            }
        }
        return roleSkillsDataArray;
    }
}
