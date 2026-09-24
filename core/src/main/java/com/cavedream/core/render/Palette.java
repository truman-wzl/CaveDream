package com.cavedream.core.render;

/**
 * 调色板（美术铁律：上色只从本表取色，任意色自动吸附到最近项）。
 * 程序生成，遵循"黑白 + RGB"：黑、白、灰阶 + HSL 色相环彩虹（每色相 × 多档明度）。
 * 全局统一、可扩容；{@link #nearestIndex(int)} 把任意 RGB 吸附到最近项。
 */
public final class Palette {

    /** 色相数（绕色环一圈取多少档）。 */
    private static final int HUES = 12;
    /** 每个色相的明度档（暗/中/亮）。 */
    private static final float[] LIGHTNESS = {0.32f, 0.5f, 0.68f, 0.84f};

    public static final int[] COLORS = build();
    public static final int SIZE = COLORS.length;

    private static int[] build() {
        java.util.List<Integer> list = new java.util.ArrayList<>();
        // 黑→白灰阶（含纯黑、纯白）
        int grays = 8;
        for (int i = 0; i <= grays; i++) {
            int v = Math.round(i / (float) grays * 255f);
            list.add(0xFF000000 | (v << 16 | v << 8 | v));
        }
        // 彩虹：HSL 色相环 × 明度档（饱和度固定偏鲜艳）
        for (int h = 0; h < HUES; h++) {
            float hue = h / (float) HUES;
            for (float l : LIGHTNESS) {
                list.add(0xFF000000 | hsl(hue, 0.8f, l));
            }
        }
        int[] a = new int[list.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = list.get(i);
        }
        return a;
    }

    /** HSL → 0xRRGGBB。h∈[0,1) 色相，s 饱和，l 明度。 */
    private static int hsl(float h, float s, float l) {
        float q = l < 0.5f ? l * (1 + s) : l + s - l * s;
        float p = 2 * l - q;
        int r = (int) Math.round(chan(p, q, h + 1f / 3f) * 255f);
        int g = (int) Math.round(chan(p, q, h) * 255f);
        int b = (int) Math.round(chan(p, q, h - 1f / 3f) * 255f);
        return (r << 16) | (g << 8) | b;
    }

    private static float chan(float p, float q, float t) {
        if (t < 0) t += 1;
        if (t > 1) t -= 1;
        if (t < 1f / 6f) return p + (q - p) * 6 * t;
        if (t < 1f / 2f) return q;
        if (t < 2f / 3f) return p + (q - p) * (2f / 3f - t) * 6;
        return p;
    }

    private Palette() {
    }

    /** 任意 RGB 吸附到最近的调色板项（欧氏距离）。 */
    public static int nearestIndex(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int best = 0, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < SIZE; i++) {
            int c = COLORS[i];
            int dr = r - ((c >> 16) & 0xFF), dg = g - ((c >> 8) & 0xFF), db = b - (c & 0xFF);
            int d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** 吸附后的 ARGB（不透明）。 */
    public static int snap(int rgb) {
        return COLORS[nearestIndex(rgb)];
    }
}
