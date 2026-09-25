package com.cavedream.core.light;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;

import java.util.ArrayDeque;

/**
 * 静态光照引擎（泰拉瑞亚式“背景墙透光”）。
 * 天光：从世界顶部开阔天空出发做**连通泛光**——凡能从天空经“无背景墙的空格”(空气/水，零衰减)抵达者=满天光；
 * 实心方块按 SOLID_COST 渐减；背景墙/雾墙完全不透光。于是：
 *  · 浮岛只是固体、不带背景墙 → 其下方空域与外界相连 → 不再被遮黑（地标/地表正午恒亮）；
 *  · 地下生成自带泥土/石背景墙 → 洞穴天然黑暗，挖开仍留墙 → 需火把。
 * 块光（矿石/火把自发光）另经 BFS 传播（穿透固体衰减），与昼夜无关。天光渲染时再按昼强缩放。
 */
public final class LightEngine {

    /** 光照最高等级（内部 0~15；对外 1~16 级）。 */
    public static final int MAX_LEVEL = 15;
    private static final int SOLID_COST = 2;   // 天光穿透固体衰减
    private static final int AIR_COST = 1;     // 块光在空气/水中每格衰减

    private int w;
    private int h;
    private byte[] sky;
    private byte[] block;

    private void ensure(LayerWorld world) {
        int nw = world.getWidth(), nh = world.getHeight();
        if (sky == null || w != nw || h != nh) {
            w = nw;
            h = nh;
            sky = new byte[w * h];
            block = new byte[w * h];
        }
    }

    /** 重算整张光照场（小规模/测试床直接整场）。 */
    public void recompute(LayerWorld world) {
        ensure(world);
        java.util.Arrays.fill(sky, (byte) 0);
        java.util.Arrays.fill(block, (byte) 0);
        computeSky(world, 0, w - 1, 0, h - 1);
        computeBlock(world, 0, w - 1, 0, h - 1);
    }

    /**
     * 局部重算：只算以 (cx,cy) 为中心、半径 r 的方框（大世界中避免整场 BFS 卡顿）。
     * 框外保留上次结果；未到达区域为 0。天光种子的“是否开阔”按整列判定（顶到首个实心/墙为止），
     * 故只需列方向扫到框顶即可，横向连通在框内泛光。
     */
    public void recomputeRegion(LayerWorld world, int cx, int cy, int r) {
        ensure(world);
        int x0 = Math.max(0, cx - r), x1 = Math.min(w - 1, cx + r);
        int y0 = Math.max(0, cy - r), y1 = Math.min(h - 1, cy + r);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int idx = y * w + x;
                sky[idx] = 0;
                block[idx] = 0;
            }
        }
        computeSky(world, x0, x1, y0, y1);
        computeBlock(world, x0, x1, y0, y1);
    }

    /** 天光：顶部开阔空气播种 + 连通泛光（空格零衰减、固体衰减、墙/雾阻挡）。 */
    private void computeSky(LayerWorld world, int x0, int x1, int y0, int y1) {
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int x = x0; x <= x1; x++) {
            boolean open = true;
            for (int y = h - 1; y >= 0; y--) {
                BlockType b = world.blockAt(x, y);
                if (!open) {
                    break;
                }
                boolean wallOrFog = b == BlockType.FOG || world.hasWall(x, y);
                if (!b.solid() && !wallOrFog) {          // 开阔空（无墙）→ 可作天光入口
                    if (y >= y0 && y <= y1) {
                        int idx = y * w + x;
                        sky[idx] = (byte) MAX_LEVEL;
                        q.add(idx);
                    }
                } else {
                    open = false;                        // 遇固体/墙/雾 → 该列开阔到此为止
                }
            }
        }
        propagateSky(world, q, x0, x1, y0, y1);
    }

    private void propagateSky(LayerWorld world, ArrayDeque<Integer> q, int x0, int x1, int y0, int y1) {
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int idx = q.poll();
            int cur = arr(sky, idx) ;
            if (cur <= 0) {
                continue;
            }
            int x = idx % w, y = idx / w;
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i], ny = y + dy[i];
                if (nx < x0 || nx > x1 || ny < y0 || ny > y1) {
                    continue;
                }
                int cost = skyCost(world, nx, ny);
                int cand = cur - cost;
                int nidx = ny * w + nx;
                if (cand > arr(sky, nidx)) {
                    sky[nidx] = (byte) cand;
                    q.add(nidx);
                }
            }
        }
    }

    /** 天光进入某格的衰减：开阔空=0（漫灌不衰减）、固体=SOLID_COST、有背景墙/雾=MAX（完全不透）。 */
    private int skyCost(LayerWorld world, int x, int y) {
        if (!world.inBounds(x, y)) {
            return MAX_LEVEL;
        }
        BlockType b = world.blockAt(x, y);
        if (b == BlockType.FOG || world.hasWall(x, y)) {
            return MAX_LEVEL;
        }
        return b.solid() ? SOLID_COST : 0;
    }

    /** 块光：自发光方块播种 + BFS（穿透固体衰减、空气衰减 AIR_COST）。 */
    private void computeBlock(LayerWorld world, int x0, int x1, int y0, int y1) {
        ArrayDeque<Integer> q = new ArrayDeque<>();
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                BlockType b = world.blockAt(x, y);
                if (b.light() > 0) {
                    int idx = y * w + x;
                    int lv = Math.min(MAX_LEVEL, b.light());
                    if (lv > (block[idx] & 0xFF)) {
                        block[idx] = (byte) lv;
                        q.add(idx);
                    }
                }
            }
        }
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int idx = q.poll();
            int cur = block[idx] & 0xFF;
            if (cur <= 1) {
                continue;
            }
            int x = idx % w, y = idx / w;
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i], ny = y + dy[i];
                if (nx < x0 || nx > x1 || ny < y0 || ny > y1) {
                    continue;
                }
                int cost = world.blockAt(nx, ny).solid() ? SOLID_COST : AIR_COST;
                int cand = cur - cost;
                int nidx = ny * w + nx;
                if (cand > (block[nidx] & 0xFF)) {
                    block[nidx] = (byte) cand;
                    q.add(nidx);
                }
            }
        }
    }

    private static int arr(byte[] a, int idx) {
        return a[idx] & 0xFF;
    }

    /** 该格静态亮度 0~15：天光按昼强缩放后与块光取最大。 */
    public int staticLevel(int x, int y, int daylight0to15) {
        if (sky == null || x < 0 || x >= w || y < 0 || y >= h) {
            return 0;
        }
        int idx = y * w + x;
        int s = ((sky[idx] & 0xFF) * daylight0to15) / MAX_LEVEL;
        int bl = block[idx] & 0xFF;
        return Math.max(s, bl);
    }

    /** 纯天光几何值 0~15（不随昼夜，供渲染判定开阔天空）；越界返回 0。 */
    public int skyAt(int x, int y) {
        if (sky == null || x < 0 || x >= w || y < 0 || y >= h) {
            return 0;
        }
        return sky[y * w + x] & 0xFF;
    }

    /** 纯块光值 0~15（供测试/调试）；越界返回 0。 */
    public int blockAt(int x, int y) {
        if (block == null || x < 0 || x >= w || y < 0 || y >= h) {
            return 0;
        }
        return block[y * w + x] & 0xFF;
    }
}
