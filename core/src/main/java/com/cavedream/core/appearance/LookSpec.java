package com.cavedream.core.appearance;

/**
 * 外观蓝图（一切皆 ID / 参数）：一个双足人形的可序列化参数集。确定性 {@link PixelLookForge} 据此
 * 锻造出像素皮肤（colors + regions），{@link LookDice} 据此投骰。字段全为 int/boolean，扁平化
 * （{@link #toParams()}/{@link #fromParams(int[])}）便于随存档持久化。
 */
public final class LookSpec {

    /** 默认长相（紫袍·长发·呆毛·飘发·披风）；作为锻造基准与旧档兜底。 */
    public static final LookSpec DEFAULT = new LookSpec(
            0x5EED2026L, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f, 0,
            0x4A3670, 0xF0D0A8, 0x222233, 0x9B72D0, 0x4B3B7A, 0x2A2244,
            (byte) 4, true, true, true, (byte) 0, true, (byte) 0);

    public long seed;
    // 体型比例（1.0=标准，安全区间内任意组合都协调）
    public float headScale;
    public float torsoScale;
    public float legScale;
    public float armScale;
    public float shoulderScale;
    // 配色
    public int paletteIndex;
    public int hairRgb;
    public int skinRgb;
    public int eyeRgb;
    public int robeRgb;
    public int pantsRgb;
    public int shoeRgb;
    // 特征
    public byte hairstyle;   // 0 光头 … 15 凌乱（见 PixelLookForge）
    public boolean ahoge;    // 呆毛
    public boolean hairFlow; // 背后飘发
    public boolean cape;     // 披风
    public byte earType;     // 0 无, 1 尖耳
    public boolean blush;    // 腮红
    public byte eyeStyle;    // 0 大眼, 1 细眼, 2 圆眼

    public LookSpec() {
    }

    public LookSpec(long seed, float headScale, float torsoScale, float legScale, float armScale,
                    float shoulderScale, int paletteIndex, int hairRgb, int skinRgb, int eyeRgb,
                    int robeRgb, int pantsRgb, int shoeRgb,
                    byte hairstyle, boolean ahoge, boolean hairFlow, boolean cape,
                    byte earType, boolean blush, byte eyeStyle) {
        this.seed = seed;
        this.headScale = headScale;
        this.torsoScale = torsoScale;
        this.legScale = legScale;
        this.armScale = armScale;
        this.shoulderScale = shoulderScale;
        this.paletteIndex = paletteIndex;
        this.hairRgb = hairRgb;
        this.skinRgb = skinRgb;
        this.eyeRgb = eyeRgb;
        this.robeRgb = robeRgb;
        this.pantsRgb = pantsRgb;
        this.shoeRgb = shoeRgb;
        this.hairstyle = hairstyle;
        this.ahoge = ahoge;
        this.hairFlow = hairFlow;
        this.cape = cape;
        this.earType = earType;
        this.blush = blush;
        this.eyeStyle = eyeStyle;
    }

    public LookSpec copy() {
        return new LookSpec(seed, headScale, torsoScale, legScale, armScale, shoulderScale,
                paletteIndex, hairRgb, skinRgb, eyeRgb, robeRgb, pantsRgb, shoeRgb,
                hairstyle, ahoge, hairFlow, cape, earType, blush, eyeStyle);
    }

    /** 扁平化为定长 int[]（21 项）供存档；boolean→0/1，float→floatToRawIntBits。 */
    public int[] toParams() {
        return new int[]{
                (int) (seed >> 32), (int) seed,
                Float.floatToRawIntBits(headScale), Float.floatToRawIntBits(torsoScale),
                Float.floatToRawIntBits(legScale), Float.floatToRawIntBits(armScale),
                Float.floatToRawIntBits(shoulderScale),
                paletteIndex, hairRgb, skinRgb, eyeRgb, robeRgb, pantsRgb, shoeRgb,
                hairstyle, ahoge ? 1 : 0, hairFlow ? 1 : 0, cape ? 1 : 0,
                earType, blush ? 1 : 0, eyeStyle};
    }

    public static LookSpec fromParams(int[] p) {
        LookSpec s = DEFAULT.copy();
        if (p == null || p.length < 21) {
            return s;
        }
        s.seed = ((long) p[0] << 32) | (p[1] & 0xFFFFFFFFL);
        s.headScale = Float.intBitsToFloat(p[2]);
        s.torsoScale = Float.intBitsToFloat(p[3]);
        s.legScale = Float.intBitsToFloat(p[4]);
        s.armScale = Float.intBitsToFloat(p[5]);
        s.shoulderScale = Float.intBitsToFloat(p[6]);
        s.paletteIndex = p[7];
        s.hairRgb = p[8];
        s.skinRgb = p[9];
        s.eyeRgb = p[10];
        s.robeRgb = p[11];
        s.pantsRgb = p[12];
        s.shoeRgb = p[13];
        s.hairstyle = (byte) p[14];
        s.ahoge = p[15] != 0;
        s.hairFlow = p[16] != 0;
        s.cape = p[17] != 0;
        s.earType = (byte) p[18];
        s.blush = p[19] != 0;
        s.eyeStyle = (byte) p[20];
        return s;
    }
}
