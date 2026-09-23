package com.cavedream.core.world.gen;

/**
 * 梦层骨架参数（GDD §2.5 要素表的字段规格书，即 LayerLayout）。
 * 四带占比 + 海参数 + 要素数量；主线层手工写死，梦海层由模板+潜度生成。
 *
 * @param width/height     层尺寸（格）
 * @param skyRatio/surfaceRatio/caveRatio/hellRatio 纵向四带占比（和为 1）
 * @param seaWidthMin/Max  每侧海宽度范围（列）
 * @param bothSeaChance    双侧海概率（GDD：25%）
 * @param pillarCount      梦锚柱数（间距≈width/柱数，L1=5）
 * @param chestCount       梦境宝箱数（含海底必刷 1）
 * @param narrativeCount   叙事碎片点数
 * @param gatheringCount   地表采集丛数
 * @param branchCount      分支回路数
 * @param islandDensity    浮岛密度（每多少个宽度 1 座）
 */
public record LayerLayout(
        int width,
        int height,
        double skyRatio,
        double surfaceRatio,
        double caveRatio,
        double hellRatio,
        int seaWidthMin,
        int seaWidthMax,
        double bothSeaChance,
        int pillarCount,
        int chestCount,
        int narrativeCount,
        int gatheringCount,
        int branchCount,
        int islandDensity
) {

    /** L1 浅梦（中世界 8400×1600，GDD §2.3/§2.5 基线）。 */
    public static LayerLayout l1() {
        return new LayerLayout(8400, 1600,
                0.15, 0.30, 0.40, 0.15,
                600, 1000, 0.25,
                5, 10, 12, 24, 12, 300);
    }

    /** 测试用小样（同比例缩壳，秒级生成）。 */
    public static LayerLayout tiny() {
        return new LayerLayout(600, 200,
                0.15, 0.30, 0.40, 0.15,
                60, 90, 0.25,
                3, 4, 3, 5, 3, 60);
    }
}
