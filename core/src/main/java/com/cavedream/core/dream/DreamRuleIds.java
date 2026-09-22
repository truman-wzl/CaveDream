package com.cavedream.core.dream;

/**
 * 法则 id 常量表。与 GDD §2.1 主线层设定及后续梦层 JSON 配置保持一致。
 */
public final class DreamRuleIds {

    /** 空间扭曲：地图通道会重排（L2 迷梦引入） */
    public static final String MAP_SHUFFLE = "map_shuffle";

    /** 重力可翻转（L3 坠梦引入） */
    public static final String GRAVITY_FLIP = "gravity_flip";

    /** 随机魇潮侵蚀安全区（L4 魇梦引入） */
    public static final String NIGHTMARE_TIDE = "nightmare_tide";

    /** 不稳定方块：随时间/梦眠度坍缩或重生（GDD §3.1） */
    public static final String UNSTABLE_BLOCKS = "unstable_blocks";

    private DreamRuleIds() {
    }
}
