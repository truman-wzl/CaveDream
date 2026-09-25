package com.cavedream.core.render;

/**
 * 可涂色外观（“一切皆 ID”）= 模板 id + 每格 ARGB 颜色数组（纯数据，可存档/传云/入库当玩家创作）。
 * 画布初始化为全黑、上覆一个默认朝右的人；<b>纯黑 = 透明键</b>——只有非黑像素构成实体
 * （为碰撞箱与后续行走动画打基础）。{@link #regions()} 提供部位划分（头发/皮肤/眼/腮红/衣袍/手臂/腿/鞋），
 * “填块”据此一键上色；画笔不限区域、整幅可涂。梦之主=模板 0；NPC/方块/武器后续各占模板 id。
 */
public final class PaintedLook {

    public static final int W = 21, H = 42;
    public static final int KEY = 0xFF000000;   // 纯黑 = 透明键（涂黑即擦除）

    public static final byte R_OUT = 0, R_HAIR = 1, R_SKIN = 2, R_EYE = 3, R_BLUSH = 4,
            R_ROBE = 5, R_ARM = 6, R_PANTS = 7, R_SHOE = 8;
    public static final String[] REGION_NAMES =
            {"背景", "头发", "皮肤", "眼睛", "腮红", "衣袍", "手臂", "裤子", "鞋子"};

    public int templateId;                 // 画盘模板 id（角色模板；物品为 -1）
    public int[] colors;                   // 每格不透明 ARGB；纯黑=透明键
    public final int w, h;                 // 画布尺寸（角色 21×42；物品取自身贴图尺寸）
    public final boolean item;             // true=物品外观（无部位划分）

    private int[] initial;                 // 重置用的起始像素
    private byte[] regionCache;

    public PaintedLook() {
        this(0);
    }

    public PaintedLook(int templateId) {
        this.item = false;
        this.w = W;
        this.h = H;
        this.templateId = templateId;
        this.colors = defaultColors(templateId);
        this.initial = this.colors.clone();
    }

    public PaintedLook(int templateId, int[] colors) {
        this.item = false;
        this.w = W;
        this.h = H;
        this.templateId = templateId;
        this.colors = colors.clone();
        this.initial = defaultColors(templateId);
    }

    private PaintedLook(int w, int h, int[] argbColors) {
        this.item = true;
        this.templateId = -1;
        this.w = w;
        this.h = h;
        this.colors = argbColors;
        this.initial = argbColors.clone();
    }

    /** 由物品自身贴图（w×h ARGB，alpha=0 处视为透明键）构造可涂外观。 */
    public static PaintedLook forItem(int w, int h, int[] argb) {
        int[] colors = new int[w * h];
        for (int i = 0; i < colors.length; i++) {
            int v = (argb != null && i < argb.length) ? argb[i] : 0;
            colors[i] = ((v >>> 24) & 0xFF) == 0 ? KEY : (0xFF000000 | (v & 0xFFFFFF));
        }
        return new PaintedLook(w, h, colors);
    }

    /** 模板默认外观：全黑画布 + 默认朝右的人（体外填黑=透明键）。 */
    public static int[] defaultColors(int templateId) {
        int[] base = BlockTextures.defaultDreamerArgb();   // 体外 alpha=0
        for (int i = 0; i < base.length; i++) {
            if (((base[i] >>> 24) & 0xFF) == 0) {
                base[i] = KEY;                             // 体外 → 全黑
            }
        }
        return base;
    }

    /** 模板的部位划分遮罩。 */
    public static byte[] regions(int templateId) {
        return BlockTextures.defaultDreamerRegions();
    }

    public byte[] regions() {
        if (item) {
            return new byte[colors.length];   // 物品无部位划分（仅画笔/橡皮有意义）
        }
        if (regionCache == null) {
            regionCache = regions(templateId);
        }
        return regionCache;
    }

    public PaintedLook copy() {
        if (item) {
            return new PaintedLook(w, h, colors.clone());
        }
        return new PaintedLook(templateId, colors);
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < w && y < h;
    }

    /** 非黑 = 有像素（实体的一部分）。 */
    public boolean opaque(int x, int y) {
        return inBounds(x, y) && (colors[y * w + x] & 0xFFFFFF) != 0;
    }

    /** 画笔：整幅画布任意格都可上色（含把体外涂黑来造型）；颜色吸附到调色板，纯黑即擦除。 */
    public void paint(int x, int y, int argb) {
        if (inBounds(x, y)) {
            colors[y * w + x] = Palette.snap(argb);
        }
    }

    /** 橡皮：擦成透明键（黑）。 */
    public void erase(int x, int y) {
        if (inBounds(x, y)) {
            colors[y * w + x] = KEY;
        }
    }

    /** 按部位整片填色（region 为 R_* 常量；颜色吸附到调色板）。 */
    public void fillRegion(byte region, int argb) {
        byte[] r = regions();
        int a = Palette.snap(argb);
        for (int i = 0; i < colors.length; i++) {
            if (r[i] == region) {
                colors[i] = a;
            }
        }
    }

    /** 大分区（按解剖四分：头·颈·躯干·四肢；颈并入头、四肢拆上下肢）。 */
    public static final String[] PART_NAMES = {"头部", "身躯", "上肢", "下肢"};

    /** 细部位 → 大分区：0头 / 1身 / 2上肢 / 3下肢（背景与描边返回 -1）。 */
    public static int partOf(byte region) {
        switch (region) {
            case R_HAIR: case R_SKIN: case R_EYE: case R_BLUSH: return 0;
            case R_ROBE: return 1;
            case R_ARM: return 2;
            case R_PANTS: case R_SHOE: return 3;
            default: return -1;
        }
    }

    /** 按大分区整片填色（保护眼睛：分区填充不覆盖 R_EYE）。 */
    public void fillPart(int part, int argb) {
        byte[] r = regions();
        int a = Palette.snap(argb);
        for (int i = 0; i < colors.length; i++) {
            byte reg = r[i];
            if (reg == R_EYE) {
                continue;
            }
            if (partOf(reg) == part) {
                colors[i] = a;
            }
        }
    }

    /** 实体包围盒（非黑像素的最小外接矩形，元素为 {x,y,w,h}）；整幅全黑返回 null。碰撞箱/动画基座。 */
    public int[] bounds() {
        int minX = w, minY = h, maxX = -1, maxY = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((colors[y * w + x] & 0xFFFFFF) != 0) {
                    if (x < minX) {
                        minX = x;
                    }
                    if (x > maxX) {
                        maxX = x;
                    }
                    if (y < minY) {
                        minY = y;
                    }
                    if (y > maxY) {
                        maxY = y;
                    }
                }
            }
        }
        if (maxX < 0) {
            return null;
        }
        return new int[]{minX, minY, maxX - minX + 1, maxY - minY + 1};
    }

    public void reset() {
        colors = initial.clone();
    }
}
