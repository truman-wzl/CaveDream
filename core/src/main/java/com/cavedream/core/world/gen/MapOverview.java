package com.cavedream.core.world.gen;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;

/**
 * 引擎无关的全图俯瞰渲染：把整层按"每格→输出像素"着色，并叠加骨架要素标记。
 * 纯 int[]（ARGB），不依赖 libGDX——离线 PNG 导出器（{@link MapExporter}）与游戏内俯览屏共用这颗心。
 *
 * <p>配色：非空气按 {@link BlockType#rgb()} 上色；空气=夜空深蓝（地形剪影一目了然）。
 * 标记：出生点/入梦锚/Boss 门/梦锚柱/宝箱/叙事点/采集丛等以醒目色小块叠印，便于一眼规划。
 */
public record MapOverview(int width, int height, int[] argb) {

    private static final int AIR_SKY = 0x0A0E1E;   // 空气（未开敞）夜空深蓝
    private static final int MARK_R = 3;           // 标记方块半径（输出像素）

    /** 只渲染方块本体（不含要素标记）。 */
    public static MapOverview render(LayerWorld w, int scale) {
        return render(w, null, scale);
    }

    /** 渲染方块 + {@link GeneratedWorld} 的骨架要素标记。scale=每输出像素代表的源格数（≥1）。 */
    public static MapOverview render(LayerWorld w, GeneratedWorld gen, int scale) {
        scale = Math.max(1, scale);
        int sw = w.getWidth(), sh = w.getHeight();
        int ow = (sw + scale - 1) / scale;
        int oh = (sh + scale - 1) / scale;
        int[] px = new int[ow * oh];
        for (int oy = 0; oy < oh; oy++) {
            for (int ox = 0; ox < ow; ox++) {
                int sx = Math.min(sw - 1, ox * scale + scale / 2);
                int sy = Math.min(sh - 1, oy * scale + scale / 2);
                BlockType b = w.blockAt(sx, sy);
                int rgb = b == BlockType.AIR ? AIR_SKY : b.rgb();
                px[oy * ow + ox] = 0xFF000000 | (rgb & 0xFFFFFF);
            }
        }
        // 输出图像 y 轴：源 (0,0) 在左下，图像习惯顶行=最高 y → 翻转
        px = flipY(px, ow, oh);
        if (gen != null) {
            stamp(px, ow, oh, scale, gen.spawn(), 0xF2F2F2);
            stamp(px, ow, oh, scale, gen.dreamEntry(), 0x38E08A);
            stamp(px, ow, oh, scale, gen.bedSite(), 0x38C6E0);
            stamp(px, ow, oh, scale, gen.dreamLoom(), 0xC86BE6);
            stamp(px, ow, oh, scale, gen.artisan(), 0xE6913A);
            stamp(px, ow, oh, scale, gen.bossGate(), 0xE23B3B);
            stamp(px, ow, oh, scale, gen.expansionVessel(), 0xFFE14D);
            for (TilePos p : gen.pillars()) {
                stamp(px, ow, oh, scale, p, 0x6FC3FF);
            }
            for (TilePos p : gen.chests()) {
                stamp(px, ow, oh, scale, p, 0xFFC94D);
            }
            for (TilePos p : gen.narrativeSpots()) {
                stamp(px, ow, oh, scale, p, 0xB07CE0);
            }
            for (TilePos p : gen.gatheringSpots()) {
                stamp(px, ow, oh, scale, p, 0x8CE04A);
            }
        }
        return new MapOverview(ow, oh, px);
    }

    private static int[] flipY(int[] src, int ow, int oh) {
        int[] out = new int[src.length];
        for (int y = 0; y < oh; y++) {
            System.arraycopy(src, (oh - 1 - y) * ow, out, y * ow, ow);
        }
        return out;
    }

    private static void stamp(int[] px, int ow, int oh, int scale, TilePos p, int rgb) {
        if (p == null) {
            return;
        }
        int cx = p.x() / scale;
        int cy = (oh - 1) - p.y() / scale;   // 与 flipY 一致的图像坐标
        for (int dy = -MARK_R; dy <= MARK_R; dy++) {
            for (int dx = -MARK_R; dx <= MARK_R; dx++) {
                if (dx * dx + dy * dy > MARK_R * MARK_R + MARK_R) {
                    continue;
                }
                int x = cx + dx, y = cy + dy;
                if (x >= 0 && x < ow && y >= 0 && y < oh) {
                    px[y * ow + x] = 0xFF000000 | (rgb & 0xFFFFFF);
                }
            }
        }
    }

    /** 标记图例（导出器/日志用）。 */
    public static String legend() {
        return "图例：白=出生点 绿=入梦锚 青=床址 紫=织梦台 橙=工匠 红=Boss门 黄=扩容器 "
                + "淡蓝=梦锚柱 金=宝箱 浅紫=叙事点 黄绿=采集丛";
    }
}
