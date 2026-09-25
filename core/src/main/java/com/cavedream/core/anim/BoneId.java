package com.cavedream.core.anim;

/**
 * 骨架骨位枚举（顺序即数组索引，切勿随意调序）。初版够支撑角色 + 前后双手 + 发梢。
 * 前后各一套肢体：z 序做前后遮挡、步态反相；{@link #HAND_F} 上的 socket 挂武器。
 * 该枚举为通用命名，未来物品/怪物的骨架可复用同一约定。
 */
public enum BoneId {
    ROOT,
    HIPS,
    SPINE,
    HEAD,
    HAIR,
    ARM_UB,     // 后侧上臂
    FORE_UB,    // 后侧小臂
    HAND_UB,
    ARM_FB,     // 前侧上臂
    FORE_FB,    // 前侧小臂
    HAND_FB,    // 前侧手（武器 socket）
    THIGH_B,    // 后大腿
    SHIN_B,
    FOOT_B,
    THIGH_F,    // 前大腿
    SHIN_F,
    FOOT_F;

    public int index() {
        return ordinal();
    }

    public static final int COUNT = 17;
}
