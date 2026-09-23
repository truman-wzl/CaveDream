package com.cavedream.core.light;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;

import java.util.ArrayDeque;

/**
 * 静态光照引擎：由天光（开放空间）与世界方块自发光（块光源）经 BFS 传播，得到每格 0~15 的亮度场。
 * 密闭洞穴靠洞口天光渗入与矿脉/火把等块光源照亮；越深越暗。天光部分在渲染时再按昼夜系数缩放。
 * 世界改动（挖/放）后调用 {@link #recompute(LayerWorld)} 重算；角色/特效等动态光不在此，由 {@link LightSource} 每帧叠加。
 */
public final class LightEngine {

    /** 光照最高等级（内部 0~15；对外 1~16 级）。 */
    public static final int MAX_LEVEL = 15;
    private static final int AIR_COST = 1;     // 光在空气/水中每格衰减
    private static final int SOLID_COST = 2;   // 光穿透固体衰减（过大会把地表也挡黑）

    private int w;
    private int h;
    private byte[] sky;
    private byte[] block;

    /** 重算整张光照场（小规模/测试床直接整场；大世界后续按 chunk 脏矩形局部化）。 */
    public void recompute(LayerWorld world) {
        int nw = world.getWidth(), nh = world.getHeight();
        if (sky == null || w != nw || h != nh) {
            w = nw;
            h = nh;
            sky = new byte[w * h];
            block = new byte[w * h];
        }
        java.util.Arrays.fill(sky, (byte) 0);
        java.util.Arrays.fill(block, (byte) 0);

        ArrayDeque<Integer> qSky = new ArrayDeque<>();
        ArrayDeque<Integer> qBlk = new ArrayDeque<>();

        for (int x = 0; x < w; x++) {
            boolean open = true;
            for (int y = h - 1; y >= 0; y--) {
                int idx = y * w + x;
                BlockType b = world.blockAt(x, y);
                // 天光种子：从顶部往下、遇非固体（天空/水面）铺满亮度；
                // 梦之雾是箱庭边界的实心封层，不能封住天光（否则整列 sky=0、永远黑夜）。
                if (open) {
                    if (!b.solid()) {
                        sky[idx] = (byte) MAX_LEVEL;
                        qSky.add(idx);
                    } else if (b != BlockType.FOG) {
                        open = false;   // 真正的地表/固体才关闭该列的天空
                    }
                }
                // 块光种子：方块自发光
                if (b.light() > 0) {
                    int lv = Math.min(MAX_LEVEL, b.light());
                    if (lv > (block[idx] & 0xFF)) {
                        block[idx] = (byte) lv;
                        qBlk.add(idx);
                    }
                }
            }
        }
        propagate(sky, qSky, world);
        propagate(block, qBlk, world);
    }

    private void propagate(byte[] arr, ArrayDeque<Integer> queue, LayerWorld world) {
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int cur = arr[idx] & 0xFF;
            if (cur <= 1) {
                continue;
            }
            int x = idx % w, y = idx / w;
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i], ny = y + dy[i];
                if (nx < 0 || nx >= w || ny < 0 || ny >= h) {
                    continue;
                }
                int nidx = ny * w + nx;
                int cost = world.blockAt(nx, ny).solid() ? SOLID_COST : AIR_COST;
                int cand = cur - cost;
                if (cand > (arr[nidx] & 0xFF)) {
                    arr[nidx] = (byte) cand;
                    queue.add(nidx);
                }
            }
        }
    }

    /**
     * 局部重算：只算以 (cx,cy) 为中心、半径 r 的方框（大世界中避免整场 BFS 卡顿）。
     * 框外保留上次结果（玩家探索过的区域不丢光）；未到达区域为 0（不可见）。
     */
    public void recomputeRegion(LayerWorld world, int cx, int cy, int r) {
        int nw = world.getWidth(), nh = world.getHeight();
        if (sky == null || w != nw || h != nh) {
            w = nw;
            h = nh;
            sky = new byte[w * h];
            block = new byte[w * h];
        }
        int x0 = Math.max(0, cx - r), x1 = Math.min(w - 1, cx + r);
        int y0 = Math.max(0, cy - r), y1 = Math.min(h - 1, cy + r);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                int idx = y * w + x;
                sky[idx] = 0;
                block[idx] = 0;
            }
        }
        ArrayDeque<Integer> qSky = new ArrayDeque<>();
        ArrayDeque<Integer> qBlk = new ArrayDeque<>();
        for (int x = x0; x <= x1; x++) {
            boolean open = true;
            for (int y = h - 1; y >= 0; y--) {
                BlockType b = world.blockAt(x, y);
                if (open) {
                    if (!b.solid()) {
                        if (y >= y0 && y <= y1) {
                            int idx = y * w + x;
                            sky[idx] = (byte) MAX_LEVEL;
                            qSky.add(idx);
                        }
                    } else if (b != BlockType.FOG) {
                        open = false;
                    }
                }
                if (b.light() > 0 && y >= y0 && y <= y1) {
                    int idx = y * w + x;
                    int lv = Math.min(MAX_LEVEL, b.light());
                    if (lv > (block[idx] & 0xFF)) {
                        block[idx] = (byte) lv;
                        qBlk.add(idx);
                    }
                }
            }
        }
        propagateBox(sky, qSky, world, x0, x1, y0, y1);
        propagateBox(block, qBlk, world, x0, x1, y0, y1);
    }

    private void propagateBox(byte[] arr, ArrayDeque<Integer> queue, LayerWorld world, int x0, int x1, int y0, int y1) {
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int cur = arr[idx] & 0xFF;
            if (cur <= 1) {
                continue;
            }
            int x = idx % w, y = idx / w;
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i], ny = y + dy[i];
                if (nx < x0 || nx > x1 || ny < y0 || ny > y1) {
                    continue;
                }
                int nidx = ny * w + nx;
                int cost = world.blockAt(nx, ny).solid() ? SOLID_COST : AIR_COST;
                int cand = cur - cost;
                if (cand > (arr[nidx] & 0xFF)) {
                    arr[nidx] = (byte) cand;
                    queue.add(nidx);
                }
            }
        }
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

    /** 纯天光几何值 0~15（不随昼夜，供测试/调试）；越界返回 0。 */
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
