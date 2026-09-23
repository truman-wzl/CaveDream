package com.cavedream.core.light;

import com.cavedream.core.world.LayerWorld;

/**
 * 方向光阴影（太阳）：给定指向太阳的单位向量，从某格沿光反方向射线步进，
 * 被固体遮挡即处于阴影中；用多条平行采样得软边（半影），产出 0~1 的可见度。
 * 纯逻辑、可脱离窗口单测。太阳低于地平线（dirY≤阈值）时不投影，返回全可见（避免晨昏掠射把地面全涂黑）。
 */
public final class SunShadow {

    private static final float MIN_ELEVATION = 0.12f;   // 低于此太阳高度不投影
    private static final int SAMPLES = 3;               // 软边采样数

    private SunShadow() {
    }

    /**
     * @param dirX,dirY 指向太阳的单位向量（dirY>0 表示太阳在上方）
     * @param maxDist   射线最大步数（格）
     * @return 0~1 天光可见度（1=完全照到，0=全阴影）
     */
    public static float visibility(LayerWorld world, int x, int y, float dirX, float dirY, int maxDist) {
        if (dirY <= MIN_ELEVATION) {
            return 1f;
        }
        // 垂直于光方向的偏移轴，用于软边多采样
        float perpX = -dirY, perpY = dirX;
        int lit = 0;
        for (int s = 0; s < SAMPLES; s++) {
            float off = (s - (SAMPLES - 1) / 2f) * 0.6f;   // -0.6,0,+0.6 格
            float ox = x + 0.5f + perpX * off;
            float oy = y + 0.5f + perpY * off;
            if (!occluded(world, ox, oy, dirX, dirY, maxDist)) {
                lit++;
            }
        }
        return lit / (float) SAMPLES;
    }

    private static boolean occluded(LayerWorld world, float sx, float sy, float dirX, float dirY, int maxDist) {
        for (int t = 1; t <= maxDist; t++) {
            int cx = (int) Math.floor(sx + dirX * t);
            int cy = (int) Math.floor(sy + dirY * t);
            if (!world.inBounds(cx, cy)) {
                return false;   // 射线出了世界=照得到天
            }
            if (world.blockAt(cx, cy).solid()) {
                return true;
            }
        }
        return false;
    }
}
