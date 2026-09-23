package com.cavedream.core.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.world.BlockType;

import java.util.Random;

/**
 * 手绘像素纹理引擎（v2，"画一样"而非 MC 式）：
 * ① 连续噪声场：每种方块生成 64×64 可平铺贴图，按世界坐标取 16×16 子块——
 *    纹理跨越格线流动，彻底消除"每格各花各的"的马赛克感；
 * ② 圆润收边：草皮唇（凸出格顶的圆角草沿）+ 凸角圆坨，轮廓是曲线不是锯齿；
 * ③ 卡通描边：暴露边缘 1px 深色勾线（shade 0.3），像笔画轮廓。
 */
public final class BlockTextures implements Disposable {

    public static final int TILE = 16;
    private static final int SHEET = 64;   // 4×4 个 tile 的连续场

    /** 供离线烘焙器导出程序化占位 PNG（引擎无关，不依赖 Pixmap 原生库）。 */
    public static final int SHEET_SIZE = 64;
    public static final int LIP_W = 16, LIP_H = 8;
    public static final int BLOB_W = 8, BLOB_H = 8;
    public static final int PICKAXE = 16;

    /** 导出某材料 64×64 连续 sheet 的 ARGB 像素（不透明）。 */
    public static int[] bakeSheetArgb(BlockType b) {
        return paintSheetArgb(b);
    }

    /** 导出草皮唇 16×8 的 ARGB（含透明）。 */
    public static int[] bakeLipArgb() {
        return paintLipArgb();
    }

    /** 导出凸角圆坨 8×8 的 ARGB（含透明）。 */
    public static int[] bakeBlobArgb() {
        return paintBlobArgb();
    }

    private final Texture[] sheets = new Texture[BlockType.values().length];
    private final TextureRegion[][][] regions =
            new TextureRegion[BlockType.values().length][4][4];
    private Texture grassLip;
    private Texture cornerBlob;
    private Texture dreamer;
    private Texture skyGrad;
    private Texture depthGrad;
    private Texture blobWhite;
    private Texture pickaxe;

    public BlockTextures() {
        for (BlockType b : BlockType.values()) {
            if (b == BlockType.AIR) {
                continue;
            }
            Pixmap pm = paintSheet(b);
            Texture t = new Texture(pm);
            t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            pm.dispose();
            sheets[b.ordinal()] = t;
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    regions[b.ordinal()][i][j] =
                            new TextureRegion(t, i * TILE, j * TILE, TILE, TILE);
                }
            }
        }
        Pixmap lip = paintLip();
        grassLip = new Texture(lip);
        grassLip.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        lip.dispose();
        Pixmap blob = paintBlob();
        cornerBlob = new Texture(blob);
        cornerBlob.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        blob.dispose();
        Pixmap pm = paintDreamer();
        dreamer = new Texture(pm);
        dreamer.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        pm.dispose();
        skyGrad = linear(paintVertical(0x0B1030, 0x35275E));   // 天顶→地平线梦紫
        depthGrad = linear(paintVertical(0x1A1A2C, 0x04040A)); // 浅穴→深渊
        Pixmap bw = paintBlobWhite();
        blobWhite = new Texture(bw);
        blobWhite.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        bw.dispose();
        Pixmap pk = argbPixmap(paintPickaxeArgb(), PICKAXE, PICKAXE);   // 透明底镐子
        pickaxe = new Texture(pk);
        pickaxe.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        pk.dispose();
    }

    private static Texture linear(Pixmap pm) {
        Texture t = new Texture(pm);
        t.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    private static Pixmap paintVertical(int topRgb, int bottomRgb) {
        Pixmap pm = new Pixmap(8, 256, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < 256; y++) {
            double t = y / 255.0;
            int r = (int) ((((topRgb >> 16) & 0xFF) * (1 - t) + ((bottomRgb >> 16) & 0xFF) * t));
            int g = (int) ((((topRgb >> 8) & 0xFF) * (1 - t) + ((bottomRgb >> 8) & 0xFF) * t));
            int b = (int) (((topRgb & 0xFF) * (1 - t) + (bottomRgb & 0xFF) * t));
            for (int x = 0; x < 8; x++) {
                put(pm, x, y, r << 16 | g << 8 | b);
            }
        }
        return pm;
    }

    private static Pixmap paintBlobWhite() {
        Pixmap pm = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double dx = x - 7.5, dy = y - 7.5;
                if (dx * dx + dy * dy <= 52) {
                    put(pm, x, y, 0xFFFFFF);
                }
            }
        }
        return pm;
    }

    /** 镐子 16×16 ARGB：木斜柄（左下→右上）+ 金属镐头（顶端弧形），透明底。 */
    private static int[] paintPickaxeArgb() {
        int[] a = new int[PICKAXE * PICKAXE];   // 默认 0 透明
        final int wood = 0x8A5A2B, woodHi = 0xA8703A, metal = 0xC8CDD8, metalD = 0x9AA0B0;
        // 柄：沿 (2,13)→(12,3) 画一段带粗细的斜线
        for (int t = 0; t <= 12; t++) {
            int hx = 2 + t;
            int hy = 13 - t;
            setPx(a, hx, hy, wood);
            setPx(a, hx, hy - 1, woodHi);
        }
        // 镐头：顶部弧形金属，跨 (6,4)→(14,6)，两端尖
        for (int x = 6; x <= 14; x++) {
            double k = (x - 10) / 4.0;
            int y = (int) Math.round(2 + k * k * 3);        // 中间高、两端低的拱
            setPx(a, x, y, metal);
            setPx(a, x, y + 1, metalD);
            if (x > 6 && x < 14) {
                setPx(a, x, y + 2, metalD);
            }
        }
        // 尖端点缀
        setPx(a, 6, 4, metal);
        setPx(a, 14, 4, metal);
        return a;
    }

    private static void setPx(int[] a, int x, int y, int rgb) {
        if (x >= 0 && x < PICKAXE && y >= 0 && y < PICKAXE) {
            a[y * PICKAXE + x] = 0xFF000000 | (rgb & 0xFFFFFF);
        }
    }

    /** 世界坐标 (x,y) 处的连续贴图子块（跨格流动）。 */
    public TextureRegion region(BlockType b, int x, int y) {
        return regions[b.ordinal()][Math.floorMod(x, 4)][Math.floorMod(y, 4)];
    }

    /** 草皮唇：盖在暴露顶边上，微微凸出格线。 */
    public Texture grassLip() {
        return grassLip;
    }

    /** 凸角圆坨（草沿转角）。 */
    public Texture cornerBlob() {
        return cornerBlob;
    }

    /** 卡通描边色（按方块基色压暗）。 */
    public static float[] outlineOf(BlockType b) {
        int rgb = shade(b.rgb(), 0.30);
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    /** 亮部色（顶面高光/水面波痕）。 */
    public static float[] lightOf(BlockType b, double f) {
        int rgb = shade(b.rgb(), f);
        return new float[]{((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f};
    }

    /** 梦之主像素小人（21×42 卡通比例，默认朝右）。 */
    public Texture dreamer() {
        return dreamer;
    }

    /** 天空渐变画布（8×256，Linear）。 */
    public Texture skyGrad() {
        return skyGrad;
    }

    /** 地下深度渐变画布（8×256，Linear）。 */
    public Texture depthGrad() {
        return depthGrad;
    }

    /** 白色圆角象限盘（染色后作任意方向凸角补圆）。 */
    public Texture blobWhite() {
        return blobWhite;
    }

    /** 手持镐子贴图（默认柄朝左下、镐头朝右上）；PICKAXE×PICKAXE 透明底。 */
    public Texture pickaxe() {
        return pickaxe;
    }

    @Override
    public void dispose() {
        for (Texture t : sheets) {
            if (t != null) {
                t.dispose();
            }
        }
        grassLip.dispose();
        cornerBlob.dispose();
        dreamer.dispose();
        skyGrad.dispose();
        depthGrad.dispose();
        blobWhite.dispose();
        pickaxe.dispose();
    }

    /* ---------------- 连续噪声贴图（可平铺 64×64） ---------------- */

    private static Pixmap paintSheet(BlockType b) {
        return opaquePixmap(paintSheetArgb(b), SHEET, SHEET);
    }

    private static int[] paintSheetArgb(BlockType b) {
        int[] a = new int[SHEET * SHEET];
        Random rnd = new Random(b.ordinal() * 7919L + 13);
        int base = sheetBase(b);
        // 8px 网格的周期值噪声（mod 8 保证无缝）
        double[][] grid = new double[8][8];
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                grid[i][j] = rnd.nextDouble();
            }
        }
        for (int y = 0; y < SHEET; y++) {
            for (int x = 0; x < SHEET; x++) {
                double n = smooth(grid, x / 8.0, y / 8.0);          // 0..1 连续
                int detail = (x * 73856093) ^ (y * 19346663) ^ (b.ordinal() * 83492791);
                double d = 0.92 + 0.16 * (((detail & 0xFF) / 255.0)); // 细颗粒
                double f = 0.80 + n * 0.38;
                int c = shade(base, f * d);
                switch (b) {
                    case WOOD -> {
                        int col = x % 16;
                        double stripe = col < 2 || col == 8 || col == 9 ? 0.78 : 1.05;
                        c = shade(base, f * stripe);
                    }
                    case DEEP_SEED -> {
                        // 三颗晶簇（含跨界副本保无缝）
                        for (int k = 0; k < 3; k++) {
                            int ox = (int) (grid[k][k] * 64), oy = (int) (grid[k + 3][k] * 64);
                            for (int dx = -8; dx <= 8; dx += 8) {
                                for (int dy = -8; dy <= 8; dy += 8) {
                                    double qx = x - (ox + dx), qy = y - (oy + dy);
                                    double r = qx * qx + qy * qy;
                                    if (r < 10) {
                                        c = shade(0x7FE8EA, 1.1 - r / 14);
                                    } else if (r < 20) {
                                        c = shade(0x49C7C9, 0.95);
                                    }
                                }
                            }
                        }
                    }
                    case HELL -> {
                        if (n > 0.82) {
                            c = shade(0xC0392B, 0.9 + d * 0.3);     // 熔岩脉络
                        }
                    }
                    case WATER -> {
                        double wave = Math.sin((x + y * 0.6) * 0.35 + n * 3) * 0.5 + 0.5;
                        c = shade(base, 0.82 + wave * 0.32);
                    }
                    case POT -> {
                        int ry = y % 16;
                        if (ry == 4 || ry == 11) {
                            c = shade(0x7A4A28, 0.95 + n * 0.1);    // 缠带
                        }
                    }
                    case CLOUD, FOG -> c = shade(base, 0.88 + n * 0.24);   // 柔云低频
                    default -> { }
                }
                a[y * SHEET + x] = 0xFF000000 | (c & 0xFFFFFF);
            }
        }
        return a;
    }

    private static int sheetBase(BlockType b) {
        return switch (b) {
            case GRASS -> 0x6B4A2F;      // 草方块主体=土，绿色交给草唇
            case MUSHROOM -> 0x7E5AA0;
            default -> b.rgb();
        };
    }

    /** 双线性平滑周期噪声采样。 */
    private static double smooth(double[][] g, double x, double y) {
        int x0 = (int) Math.floor(x) & 7, y0 = (int) Math.floor(y) & 7;
        int x1 = (x0 + 1) & 7, y1 = (y0 + 1) & 7;
        double tx = x - Math.floor(x), ty = y - Math.floor(y);
        tx = tx * tx * (3 - 2 * tx);
        ty = ty * ty * (3 - 2 * ty);
        double a = g[x0][y0], b2 = g[x1][y0], c2 = g[x0][y1], d2 = g[x1][y1];
        return a + (b2 - a) * tx + (c2 - a) * ty + (a - b2 - c2 + d2) * tx * ty;
    }

    /* ---------------- 草皮唇 & 圆坨 ---------------- */

    private static Pixmap paintLip() {
        return argbPixmap(paintLipArgb(), LIP_W, LIP_H);
    }

    private static int[] paintLipArgb() {
        int[] a = new int[LIP_W * LIP_H];
        for (int x = 0; x < 16; x++) {
            int h = 4 + (int) Math.round(2.2 * Math.sin(x * 0.55 + 1.2));   // 起伏草沿
            for (int y = 0; y < h; y++) {
                int c = y == 0 ? shade(0x67B34A, 1.12)                        // 顶亮边
                        : y >= h - 2 ? shade(0x4C8A3E, 0.72)                  // 根须渐暗
                        : shade(0x57A044, 0.95 + 0.1 * Math.sin(x * 1.7));
                a[y * LIP_W + x] = 0xFF000000 | (c & 0xFFFFFF);
            }
            // 1px 描边勾在草沿头顶
            int ey = Math.max(0, (int) Math.round(2.2 * Math.sin(x * 0.55 + 1.2)) - 1);
            a[ey * LIP_W + x] = 0xFF000000 | (shade(0x2F5A28, 1.0) & 0xFFFFFF);
        }
        return a;
    }

    private static Pixmap paintBlob() {
        return argbPixmap(paintBlobArgb(), BLOB_W, BLOB_H);
    }

    private static int[] paintBlobArgb() {
        int[] a = new int[BLOB_W * BLOB_H];
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                double dx = x - 7.5, dy = y - 7.5;                           // 圆心在右下：左上象限为实体
                double r2 = dx * dx + dy * dy;
                if (r2 <= 49) {
                    int bc = r2 > 36 ? shade(0x2F5A28, 1.0)             // 弧上描边
                            : shade(0x57A044, 0.95 + (7 - y) * 0.02);
                    a[y * BLOB_W + x] = 0xFF000000 | (bc & 0xFFFFFF);
                }
            }
        }
        return a;
    }

    /* ---------------- 梦之主小人 ---------------- */

    private static final int D_W = 21;
    private static final int D_H = 42;

    /** 卡通像素小人：Q 版大头、圆脸闭眼、腮红小鼻、长袍前臂、双腿错位 + 1px 自动描边。 */
    private static Pixmap paintDreamer() {
        final int skin = 0xF0D0A8, skinShade = 0xD8B088, blush = 0xE8A0A0;
        final int hair = 0x4A3670, hairHi = 0x5D458A;
        final int robe = 0x8E7CC3, robeD = 0x6E5DA6, robeL = 0xA99AD8;
        final int arm = 0xA494D6, pants = 0x4A4A66, pantsD = 0x3A3A52;
        final int shoe = 0x2E2E40, eye = 0x222233, outline = 0x1A1426;

        int[][] grid = new int[D_H][D_W];
        for (int y = 0; y < D_H; y++) {
            for (int x = 0; x < D_W; x++) {
                grid[y][x] = dreamerPixel(x, y, skin, skinShade, blush, hair, hairHi,
                        robe, robeD, robeL, arm, pants, pantsD, shoe, eye);
            }
        }
        for (int y = 0; y < D_H; y++) {
            for (int x = 0; x < D_W; x++) {
                if (grid[y][x] != 0 || !nearBody(grid, x, y)) {
                    continue;
                }
                grid[y][x] = outline;
            }
        }
        Pixmap pm = new Pixmap(D_W, D_H, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();   // 强制透明底（修复人物矩形背景：某些环境 Pixmap 初值非全透明）
        for (int y = 0; y < D_H; y++) {
            for (int x = 0; x < D_W; x++) {
                if (grid[y][x] != 0) {
                    put(pm, x, y, grid[y][x]);
                }
            }
        }
        return pm;
    }

    private static boolean nearBody(int[][] grid, int x, int y) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0], ny = y + d[1];
            if (nx >= 0 && nx < D_W && ny >= 0 && ny < D_H && grid[ny][nx] != 0) {
                return true;
            }
        }
        return false;
    }

    /** 逐像素判定（坐标系：y 向下，0=头顶；默认面朝右）。 */
    private static int dreamerPixel(int x, int y,
                                    int skin, int skinShade, int blush, int hair, int hairHi,
                                    int robe, int robeD, int robeL, int arm,
                                    int pants, int pantsD, int shoe, int eye) {
        double hdx = x - 9.5, hdy = y - 11;
        boolean inHead = hdx * hdx + hdy * hdy <= 46.0;
        if (inHead) {
            if (y <= 8.5 || (y <= 12.5 && x <= 6.5)) {
                return (y >= 7 && y <= 8 && x >= 8 && x <= 11) ? hairHi : hair;
            }
            if (y >= 11 && y < 12.4 && x >= 12 && x <= 14) {
                return eye;
            }
            if (x >= 15 && x <= 16 && y >= 11.5 && y <= 13) {
                return skinShade;
            }
            if (x >= 12.5 && x <= 14 && y >= 13.5 && y <= 14.5) {
                return blush;
            }
            return skin;
        }
        if (y >= 15.5 && y < 17 && x >= 8.5 && x <= 11.5) {
            return skin;
        }
        if (y >= 17 && y < 28) {
            int x0 = y < 19 ? 7 : y < 24 ? 6 : 5;
            int x1 = y < 19 ? 12 : y < 24 ? 13 : 14;
            if (x >= x0 && x <= x1) {
                if (y >= 17 && y < 18.4 && x >= 8 && x <= 12) {
                    return robeL;
                }
                if (y >= 26.6) {
                    return robeD;
                }
                return x < 8.5 ? robeD : robe;
            }
        }
        if (x >= 12 && x <= 14.5 && y >= 19 && y < 25) {
            return arm;
        }
        if (Math.pow(x - 13.5, 2) + Math.pow(y - 25.6, 2) <= 3.2) {
            return skin;
        }
        if (y >= 28 && y < 38.5) {
            if (x >= 8.5 && x <= 12) {
                return pants;
            }
            if (x >= 5 && x <= 8) {
                return pantsD;
            }
        }
        if (y >= 38.5 && y < 41) {
            if (x >= 8.5 && x <= 14.5) {
                return shoe;
            }
            if (x >= 4 && x <= 8) {
                return robeD;
            }
        }
        return 0;
    }

    /* ---------------- 工具 ---------------- */

    private static void put(Pixmap pm, int x, int y, int rgb) {
        pm.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        pm.drawPixel(x, y);
    }

    /** ARGB 数组（0xAARRGGBB）→ 不透明 Pixmap（忽略 alpha，全部视为实色）。 */
    private static Pixmap opaquePixmap(int[] argb, int w, int h) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                put(pm, x, y, argb[y * w + x] & 0xFFFFFF);
            }
        }
        return pm;
    }

    /** ARGB 数组 → 带透明 Pixmap（a==0 处保持透明）。 */
    private static Pixmap argbPixmap(int[] argb, int w, int h) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();   // 强制透明底，a==0 处保持真正透明（唇/圆角不露矩形背景）
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int v = argb[y * w + x];
                if (((v >>> 24) & 0xFF) == 0) {
                    continue;
                }
                pm.setColor(((v >> 16) & 0xFF) / 255f, ((v >> 8) & 0xFF) / 255f, (v & 0xFF) / 255f,
                        ((v >>> 24) & 0xFF) / 255f);
                pm.drawPixel(x, y);
            }
        }
        return pm;
    }

    private static int shade(int rgb, double factor) {
        int r = (int) Math.min(255, Math.max(0, ((rgb >> 16) & 0xFF) * factor));
        int g = (int) Math.min(255, Math.max(0, ((rgb >> 8) & 0xFF) * factor));
        int b = (int) Math.min(255, Math.max(0, (rgb & 0xFF) * factor));
        return r << 16 | g << 8 | b;
    }
}
