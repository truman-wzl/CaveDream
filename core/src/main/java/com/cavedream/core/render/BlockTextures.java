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
    public static final int COIN_SIZE = 16;
    /** 整体树贴图（透明底，供世界内按 3×H 矩形纵向拉伸）。 */
    public static final int TREE_TEX_W = 48, TREE_TEX_H = 240;

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
    private Texture coin;
    private Texture tree;

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
        Pixmap cn = argbPixmap(paintCoinArgb(), COIN_SIZE, COIN_SIZE);   // 透明底银币
        coin = new Texture(cn);
        coin.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        cn.dispose();
        Pixmap tr = argbPixmap(paintTreeArgb(), TREE_TEX_W, TREE_TEX_H);   // 透明底整树
        tree = new Texture(tr);
        tree.setFilter(TextureFilter.Linear, TextureFilter.Linear);        // 纵向拉伸→线性更平滑
        tr.dispose();
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

    /** 银色铸梦币 16×16 ARGB：圆形币面 + 暗边 + 左上高光 + 中心浮雕，透明底。 */
    private static int[] paintCoinArgb() {
        int[] a = new int[COIN_SIZE * COIN_SIZE];
        for (int y = 0; y < COIN_SIZE; y++) {
            for (int x = 0; x < COIN_SIZE; x++) {
                double dx = x - 7.5, dy = y - 7.5;
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d > 7) {
                    continue;
                }
                int c;
                if (d >= 5.8) {
                    c = 0x6E7688;                       // 暗边
                } else if (dx < -1 && dy < -1) {
                    c = 0xF2F6FC;                       // 左上高光
                } else if (d < 2.2) {
                    c = 0xAEB6C4;                       // 中心浮雕
                } else {
                    c = 0xC3CAD6;                       // 银面
                }
                a[y * COIN_SIZE + x] = 0xFF000000 | (c & 0xFFFFFF);
            }
        }
        return a;
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

    /** 手持铸梦币贴图（银色圆币）；COIN_SIZE×COIN_SIZE 透明底。 */
    public Texture coin() {
        return coin;
    }

    /** 整体树贴图（透明底）；TREE_TEX_W×TREE_TEX_H，世界内按树矩形拉伸。 */
    public Texture tree() {
        return tree;
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
        coin.dispose();
        tree.dispose();
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
                    case DOOR -> {
                        int tx = x % 16, ty = y % 16;                        // 每 16×16 一整枚门
                        boolean frame = tx <= 1 || tx >= 14 || ty <= 1 || ty >= 14;
                        boolean seam = tx == 7 || tx == 8;
                        if (frame) {
                            c = shade(0x4A2E18, 1.0);                         // 深木框
                        } else if (seam) {
                            c = shade(base, 0.72);                           // 板缝
                        } else {
                            c = shade(base, 0.92 + ((tx / 2) % 2) * 0.16);    // 竖向木板明暗
                        }
                        if (tx >= 11 && tx <= 12 && ty >= 7 && ty <= 8) {
                            c = 0xE8C34A;                                    // 金把手
                        }
                    }
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
            int h = 5 + (int) Math.round(1.4 * Math.sin(x * 0.9 + 1.2));   // 轻微起伏的草沿
            for (int y = 0; y < h; y++) {
                int c = y == 0 ? shade(0x6FBF52, 1.10)                       // 顶亮边
                        : y >= h - 2 ? shade(0x4C8A3E, 0.78)                 // 根须渐暗入土
                        : shade(0x57A044, 0.95 + 0.08 * Math.sin(x * 1.9));
                a[y * LIP_W + x] = 0xFF000000 | (c & 0xFFFFFF);
            }
            // 不再画 wavy 深色弧形描边（旧弧线逐格重复→连成眼镜状）；顶行亮绿已自成一刃轮廓。
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

    /** 梦之主像素小人（21×42 Q 版大头）：刘海盖额、大眼、腮红、背后一束飘逸长发 + 头顶呆毛，长袍前臂、双腿错位 + 1px 自动描边。 */
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

    /** 逐像素判定（坐标系：y 向下，0=头顶；面朝右）。重画为分离肢体：前臂右、后臂左、双腿带中缝、分左/右脚。 */
    private static int dreamerPixel(int x, int y,
                                    int skin, int skinShade, int blush, int hair, int hairHi,
                                    int robe, int robeD, int robeL, int arm,
                                    int pants, int pantsD, int shoe, int eye) {
        double hdx = x - 9.5, hdy = y - 10;
        boolean inHead = hdx * hdx + hdy * hdy <= 40.0;
        // 呆毛
        if ((x == 9 || x == 10) && y >= 1 && y <= 3) {
            return (x == 10 && y <= 2) ? hairHi : hair;
        }
        if (inHead) {
            if (y <= 9 || (x <= 4 && y <= 15)) {                 // 刘海 + 左侧后发
                return (y >= 5 && y <= 7 && x >= 8 && x <= 12) ? hairHi : hair;
            }
            if (x >= 11 && x <= 13 && y >= 11 && y <= 13) {
                return eye;                                        // 右视大眼
            }
            if (x >= 14 && x <= 15 && y >= 12 && y <= 13) {
                return skinShade;                                  // 鼻
            }
            if (x >= 12 && x <= 14 && y >= 14 && y <= 15) {
                return blush;                                      // 腮红
            }
            return skin;
        }
        if (y >= 15 && y <= 17 && x >= 9 && x <= 11) {
            return skin;                                           // 脖
        }
        if (y >= 17 && y < 28) {                                   // 躯干（袍）
            int x0 = y < 20 ? 6 : 5;
            int x1 = y < 20 ? 14 : 15;
            if (x >= x0 && x <= x1) {
                if (y < 18) {
                    return robeL;
                }
                if (y >= 26) {
                    return robeD;
                }
                return x < 10 ? robeD : robe;
            }
        }
        if (x >= 1 && x <= 4 && y >= 18 && y < 28) {
            return arm;                                             // 后臂（整条 R_ARM，含手）
        }
        if (x >= 16 && x <= 19 && y >= 18 && y < 28) {
            return arm;                                             // 前臂（整条 R_ARM，含手）
        }
        if (y >= 28 && y < 39) {                                   // 双腿（中缝 x10）
            if (x >= 11 && x <= 15) {
                return pants;
            }
            if (x >= 5 && x <= 9) {
                return pantsD;
            }
        }
        if (y >= 39 && y < 42) {                                   // 双脚（朝右前脚较长）
            if (x >= 11 && x <= 16) {
                return shoe;
            }
            if (x >= 4 && x <= 9) {
                return shoe;
            }
        }
        return 0;
    }

    /** 梦之主底模的默认 ARGB 网格（21×42，含描边，0=透明），供上色画板初始化。 */
    public static int[] defaultDreamerArgb() {
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
        int[] argb = new int[D_W * D_H];
        for (int y = 0; y < D_H; y++) {
            for (int x = 0; x < D_W; x++) {
                int c = grid[y][x];
                argb[y * D_W + x] = c == 0 ? 0 : (0xFF000000 | (c & 0xFFFFFF));
            }
        }
        return argb;
    }

    /** 梦之主底模每格的部位划分（与默认配色一致，靠颜色反查）：0背景/边、1发、2肤、3眼、4腮红、5衣袍、6臂、7裤、8鞋。 */
    public static byte[] defaultDreamerRegions() {
        int[] argb = defaultDreamerArgb();
        byte[] r = new byte[argb.length];
        for (int i = 0; i < argb.length; i++) {
            r[i] = ((argb[i] >>> 24) & 0xFF) == 0 ? 0 : regionOf(argb[i] & 0xFFFFFF);
        }
        return r;
    }

    private static byte regionOf(int rgb) {
        switch (rgb) {
            case 0x4A3670: case 0x5D458A: return 1;               // 头发
            case 0xF0D0A8: case 0xD8B088: return 2;               // 皮肤
            case 0x222233: return 3;                              // 眼睛
            case 0xE8A0A0: return 4;                              // 腮红
            case 0x8E7CC3: case 0x6E5DA6: case 0xA99AD8: return 5; // 衣袍
            case 0xA494D6: return 6;                              // 手臂
            case 0x4A4A66: case 0x3A3A52: return 7;               // 裤子
            case 0x2E2E40: return 8;                              // 鞋子
            default: return 0;                                    // 描边/其它→不作可涂区域
        }
    }

    /** 方块默认 16×16 贴图 ARGB（取 sheet 左上格），供法典画板按物品自身贴图起始。 */
    public static int[] blockArgb(BlockType b) {
        int[] sheet = paintSheetArgb(b);
        int[] t = new int[TILE * TILE];
        for (int y = 0; y < TILE; y++) {
            System.arraycopy(sheet, y * SHEET, t, y * TILE, TILE);
        }
        return t;
    }

    /** 铸梦币默认 16×16 贴图 ARGB。 */
    public static int[] coinArgb() {
        return paintCoinArgb();
    }

    /* ---------------- 工具 ---------------- */

    /** 整体树 ARGB（48×240，透明底）：中心树干 + 底部张开贴地根 + 顶部蓬松树冠（多圆叠加）。 */
    private static int[] paintTreeArgb() {
        final int W = TREE_TEX_W, H = TREE_TEX_H;
        final int cx = W / 2;
        final int trunkTopY = (int) (H * 0.40);
        final int[] greens = {0x2F6B2A, 0x3E7A2E, 0x4C8A3E, 0x5FA24A};
        final double cy = H * 0.22;
        final double[] bx = {-14, 14, 0, -20, 20, -6, 6, 0};   // 树冠各团圆（相对中心）
        final double[] by = {8, 8, -14, 20, 20, 22, 22, 0};
        final double[] br = {20, 20, 20, 15, 15, 16, 16, 26};
        int[] a = new int[W * H];
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int argb = 0;
                // 树干 + 贴地根（底部数行向外张开）
                if (y >= trunkTopY) {
                    int fromBottom = H - 1 - y;
                    double hw = 2.6;
                    if (fromBottom < 16) {
                        hw += (16 - fromBottom) * 0.42;
                    }
                    double dx = Math.abs(x - cx);
                    if (dx <= hw) {
                        int wood = dx > hw - 1.1 ? 0x4A2E18 : 0x6E4A28;   // 边缘描深
                        argb = 0xFF000000 | (wood & 0xFFFFFF);
                    }
                }
                // 树冠（覆盖树干顶→叶在前）
                double shade01 = 0.72 + 0.28 * (y / (double) H);           // 靠下略暗
                for (int k = 0; k < bx.length; k++) {
                    double dx = x - (cx + bx[k]), dy = y - (cy + by[k]);
                    if (dx * dx + dy * dy < br[k] * br[k]) {
                        int base = greens[Math.abs(x * 7 + y * 13) % greens.length];
                        argb = 0xFF000000 | (shade(base, shade01) & 0xFFFFFF);
                        break;
                    }
                }
                a[y * W + x] = argb;
            }
        }
        return a;
    }

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
