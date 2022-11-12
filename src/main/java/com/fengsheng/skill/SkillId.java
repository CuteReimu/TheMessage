package com.fengsheng.skill;

public enum SkillId {
    LIAN_LUO("联络"), MING_ER("明饵"), XIN_SI_CHAO("新思潮"), MIAN_LI_CANG_ZHEN("绵里藏针"),
    QI_HUO_KE_JU("奇货可居"), JIN_SHEN("谨慎"), GUI_ZHA("诡诈"), YI_YA_HUAN_YA("以牙还牙"),
    TOU_TIAN("偷天"), HUAN_RI("换日"), YI_HUA_JIE_MU("移花接木"), LIAN_MIN("怜悯"),
    FU_HEI("腹黑"), SHI_SI("视死"), RU_GUI("如归"), JI_ZHI("集智"), CHENG_ZHI("承志"),
    WEI_SHENG("尾声"), JIU_JI("就计"), CHENG_FU("城府"), YI_XIN("遗信"), ZHI_YIN("知音"),
    JING_MENG("惊梦"), JIE_DAO_SHA_REN("借刀杀人"), JIAO_JI("交际"), JI_SONG("急送"), ZHUAN_JIAO("转交"),
    MIAO_BI_QIAO_BIAN("妙笔巧辩"), JIN_BI("禁闭"), BEI_JIN_BI("被禁闭"), JIN_KOU_YI_KAI("金口一开"), JI_BAN("羁绊"),
    YING_BIAN("应变"), YOU_DAO("诱导"), BO_AI("博爱"), GUANG_FA_BAO("广发报"), QIANG_LING("强令"),
    JIAN_REN("坚韧"), MIAO_SHOU("妙手"), SOU_JI("搜辑"), JIANG_HU_LING("江湖令"), JIANG_HU_LING2("江湖令2"),
    DUI_ZHENG_XIA_YAO("对症下药");

    private final String name;

    SkillId(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
