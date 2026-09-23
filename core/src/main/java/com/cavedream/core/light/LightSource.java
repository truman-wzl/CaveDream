package com.cavedream.core.light;

/**
 * 一个点光源（tile 坐标）。既承载角色自身微光，也承载装备/饰品/时装/武器特效造成的"光波、光晕"。
 * 每帧由 PlayScreen 收集为动态光源列表，参与格子亮度合成（局部径向衰减，无需全局 BFS）。
 *
 * @param tileX   光源所在格 x
 * @param tileY   光源所在格 y
 * @param level   峰值亮度 0~15（内部；对外 1~16 = +1）
 * @param radius  影响半径（格），边缘衰减到 0
 */
public record LightSource(int tileX, int tileY, int level, float radius) {

    /** 该光源在 (x,y) 处贡献的亮度 0~15（超出半径为 0，线性衰减）。 */
    public int at(int x, int y) {
        if (level <= 0 || radius <= 0f) {
            return 0;
        }
        double dx = x + 0.5 - tileX;
        double dy = y + 0.5 - tileY;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d >= radius) {
            return 0;
        }
        double falloff = 1.0 - d / radius;          // 1→0
        int v = (int) Math.round(level * falloff * falloff);   // 平方衰减更柔和
        return Math.max(0, Math.min(LightEngine.MAX_LEVEL, v));
    }
}
