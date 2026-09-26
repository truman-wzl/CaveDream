package com.cavedream.core.appearance;

import java.util.Random;

/**
 * 骰子：从"怎么投都好看"的受控参数空间里取一套 {@link LookSpec}。
 * 关键护栏：体型比例只在窄区间内浮动、配色只从若干**预调协调调色板**里取 → 任意组合都不丑。
 * 纯函数（seed 决定一切），可复现。
 */
public final class LookDice {

    /** 每套 = {发, 肤, 袍, 裤, 鞋, 眼}（袍/裤/鞋为服装主色，做鲜艳、色相拉开）。 */
    private static final int[][] PALETTES = {
            {0x4A3670, 0xF0D0A8, 0x9B72D0, 0x4B3B7A, 0x2A2244, 0x222233}, // 通灵·紫
            {0x2E5A88, 0xEAC79E, 0x4FA0E0, 0x2E5A8C, 0x1E3550, 0x1A2230}, // 寒星·蓝
            {0x7A3B3B, 0xF2D2A8, 0xE0604F, 0x8C2E2E, 0x401818, 0x2A1A1A}, // 熔梦·赤
            {0x3E6B3A, 0xE8CFA0, 0x5FBF6A, 0x2E7A3A, 0x1A4020, 0x1E2A1C}, // 林语·翠
            {0x6E5A2E, 0xF0D8B0, 0xF0B840, 0x9A6A20, 0x4A3418, 0x241E12}, // 流金·琥
            {0x555A66, 0xE0DAD0, 0xBFC6D6, 0x6A7280, 0x30343C, 0x181A20}, // 霜铁·银
            {0x8A4A7A, 0xF2D8E0, 0xF07AB8, 0x9A3A6E, 0x4A1E38, 0x241420}, // 樱醉·粉
            {0x2E6E6E, 0xE6D2B0, 0x4FD0C0, 0x2E7A72, 0x1A403C, 0x142828}, // 梦水·青
    };
    private static final int PALETTE_COUNT = PALETTES.length;

    /** 肤色（人类命运共同体：极浅→白→黄→棕→深，12 档渐变）。 */
    private static final int[] SKINS = {
            0xFFE7CE, 0xFCDCC0, 0xF7D0A9, 0xF2C49A, 0xEAC0A0, 0xE0AC7E,
            0xD9A066, 0xC68642, 0xA96A42, 0x8D5524, 0x6E4326, 0x5C3A21};
    /** 发色（黑/深棕/棕/金/浅金/银白/红棕 + 少量梦幻紫蓝）。 */
    private static final int[] HAIRS = {
            0x1A1420, 0x2E2118, 0x6B4A2A, 0x8A5A2E, 0xB8860B, 0xD9A066, 0xE4E4E8, 0x7A3B2E, 0x4A3670, 0x2E4A6E};
    /** 瞳色（黑/棕/淡褐/蓝/绿/琥珀/灰）。 */
    private static final int[] EYES = {
            0x2A2233, 0x3A2A1A, 0x5A3A22, 0x2E4A6E, 0x2E6E4A, 0x6E5A2E, 0x4A4A55};
    /** 发型数（≥16）。 */
    public static final int HAIRSTYLE_COUNT = 16;

    public static int paletteCount() {
        return PALETTE_COUNT;
    }

    /** 取某调色板的袍色（供骰子外的 UI 预览/图例）。 */
    public static int robeOf(int paletteIndex) {
        int[] pal = PALETTES[Math.floorMod(paletteIndex, PALETTE_COUNT)];
        return pal[2];
    }

    /** 取某调色板全套（发/肤/袍/裤/鞋/眼）；单一真源，供锻造器与骰子共用。 */
    public static int[] paletteAt(int paletteIndex) {
        return PALETTES[Math.floorMod(paletteIndex, PALETTE_COUNT)];
    }

    public static LookSpec roll(long seed) {
        Random r = new Random(seed);
        LookSpec s = new LookSpec();
        s.seed = seed;
        s.headScale = lerp(r, 0.9f, 1.12f);
        s.torsoScale = lerp(r, 0.92f, 1.08f);
        s.legScale = lerp(r, 0.9f, 1.12f);
        s.armScale = lerp(r, 0.94f, 1.06f);
        s.shoulderScale = lerp(r, 0.9f, 1.1f);
        float hue = r.nextFloat();                       // RGB 彩虹：服装色相走满色轮、饱和明亮
        s.paletteIndex = (int) (hue * PALETTE_COUNT) % PALETTE_COUNT;
        s.robeRgb = hsl(hue, 0.66f, 0.56f);
        s.pantsRgb = hsl((hue + 0.08f) % 1f, 0.42f, 0.36f);
        s.shoeRgb = hsl((hue + 0.08f) % 1f, 0.30f, 0.17f);
        s.hairRgb = HAIRS[r.nextInt(HAIRS.length)];
        s.skinRgb = SKINS[r.nextInt(SKINS.length)];
        s.eyeRgb = EYES[r.nextInt(EYES.length)];
        s.hairstyle = (byte) r.nextInt(HAIRSTYLE_COUNT);
        s.ahoge = r.nextBoolean();
        s.hairFlow = r.nextBoolean();
        s.cape = r.nextBoolean();
        s.earType = (byte) (r.nextInt(4) == 0 ? 1 : 0);
        s.blush = r.nextInt(3) != 0;
        s.eyeStyle = (byte) r.nextInt(3);
        return s;
    }

    /** 只重掷 base 中未锁定的槽（locked 掩码位=1 表示保留 base 对应字段）。 */
    public static LookSpec reroll(LookSpec base, long newSeed, int locked) {
        LookSpec rolled = roll(newSeed);
        LookSpec out = base.copy();
        out.seed = newSeed;
        if ((locked & BODY) == 0) {
            out.headScale = rolled.headScale;
            out.torsoScale = rolled.torsoScale;
            out.legScale = rolled.legScale;
            out.armScale = rolled.armScale;
            out.shoulderScale = rolled.shoulderScale;
        }
        if ((locked & PALETTE) == 0) {
            out.paletteIndex = rolled.paletteIndex;
            out.hairRgb = rolled.hairRgb;
            out.skinRgb = rolled.skinRgb;
            out.eyeRgb = rolled.eyeRgb;
            out.robeRgb = rolled.robeRgb;
            out.pantsRgb = rolled.pantsRgb;
            out.shoeRgb = rolled.shoeRgb;
        }
        if ((locked & HAIR) == 0) {
            out.hairstyle = rolled.hairstyle;
            out.ahoge = rolled.ahoge;
            out.hairFlow = rolled.hairFlow;
        }
        if ((locked & EXTRAS) == 0) {
            out.cape = rolled.cape;
            out.earType = rolled.earType;
            out.blush = rolled.blush;
            out.eyeStyle = rolled.eyeStyle;
        }
        return out;
    }

    public static final int BODY = 1, PALETTE = 2, HAIR = 4, EXTRAS = 8;

    private static float lerp(Random r, float lo, float hi) {
        return lo + (hi - lo) * r.nextFloat();
    }

    /** HSL → 0xRRGGBB（h∈[0,1) 色相，s 饱和，l 明度）。 */
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

    private LookDice() {
    }
}
