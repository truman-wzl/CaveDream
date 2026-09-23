package com.cavedream.core.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.world.BlockType;

import java.util.Random;

/**
 * M2-a 程序化像素纹理：运行时生成 16×16 方块贴图（零素材依赖）。
 * 手法：基色亮度噪声 + 各方块专属纹理图案 + 每块 4 个变体（按世界坐标哈希选版，
 * 消除"同色大平面"与网格重复感）；附赠梦之主像素小人。
 * Pixmap 坐标约定：y 向下（0=贴图顶行），libGDX 绘制时自动翻转。
 */
public final class BlockTextures implements Disposable {

    public static final int SIZE = 16;
    public static final int VARIANTS = 4;

    private final Texture[][] tiles = new Texture[BlockType.values().length][VARIANTS];
    private final Texture dreamer;

    public BlockTextures() {
        for (BlockType b : BlockType.values()) {
            if (b == BlockType.AIR) {
                continue;
            }
            for (int v = 0; v < VARIANTS; v++) {
                Pixmap pm = paintBlock(b, v);
                Texture t = new Texture(pm);
                t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
                pm.dispose();
                tiles[b.ordinal()][v] = t;
            }
        }
        Pixmap pm = paintDreamer();
        dreamer = new Texture(pm);
        dreamer.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        pm.dispose();
    }

    /** 取方块贴图；variant 任意整数（内部取模）。 */
    public Texture get(BlockType block, int variant) {
        return tiles[block.ordinal()][Math.floorMod(variant, VARIANTS)];
    }

    /** 梦之主像素小人（21×42 卡通比例，默认朝右侧脸，按碰撞盒 20.8×41.6 约 1:1 绘制）。 */
    public Texture dreamer() {
        return dreamer;
    }

    @Override
    public void dispose() {
        for (Texture[] row : tiles) {
            for (Texture t : row) {
                if (t != null) {
                    t.dispose();
                }
            }
        }
        dreamer.dispose();
    }

    /* ---------------- 方块纹理工厂 ---------------- */

    private static Pixmap paintBlock(BlockType b, int variant) {
        Pixmap pm = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
        Random rnd = new Random(b.ordinal() * 977L + variant * 31L + 7);
        int base = b.rgb();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double j = 0.86 + rnd.nextDouble() * 0.26;   // 全局亮度噪声
                int c;
                switch (b) {
                    case GRASS -> {
                        // 顶 4 行草层 + 参差过渡，其下泥土
                        boolean grassRow = y < 4 || (y == 4 && rnd.nextDouble() < 0.45);
                        c = grassRow
                                ? shade(0x4C8A3E, 0.82 + rnd.nextDouble() * 0.36)
                                : shade(0x6B4A2F, j);
                    }
                    case DIRT -> c = rnd.nextDouble() < 0.05
                            ? shade(base, 0.68)                       // 深色土粒
                            : shade(base, j);
                    case STONE -> c = rnd.nextDouble() < 0.07
                            ? shade(base, 0.70)                       // 裂纹暗斑
                            : shade(base, j);
                    case DEEP_SEED -> {
                        if (rnd.nextDouble() < 0.14) {
                            c = shade(0x49C7C9, 0.9 + rnd.nextDouble() * 0.5); // 青色晶粒
                        } else {
                            c = rnd.nextDouble() < 0.06 ? shade(0x55525E, 0.72) : shade(0x55525E, j);
                        }
                    }
                    case WOOD -> {
                        int col = (x + variant) % 4;                  // 纵向纹理条纹
                        double f = col == 0 ? 0.80 : col == 2 ? 1.08 : 0.95;
                        c = shade(base, j * 0.4 + f * 0.6);
                    }
                    case LEAF -> c = rnd.nextDouble() < 0.10
                            ? shade(base, 0.58)                       // 叶隙暗点
                            : shade(base, j * 1.05);
                    case FOG -> {
                        // 4×4 团块噪声：低频明暗 + 细噪声，雾的涌动
                        int cell = ((y / 4) * 4 + (x / 4)) * 131 + variant * 17;
                        double low = 0.80 + ((cell % 7) / 7.0) * 0.45;
                        c = shade(base, j * 0.35 + low * 0.65);
                    }
                    default -> c = shade(base, j);
                }
                put(pm, x, y, c);
            }
        }
        return pm;
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
        // 自动 1px 描边：四周邻域有实体即描边（卡通像素风的关键）
        for (int y = 0; y < D_H; y++) {
            for (int x = 0; x < D_W; x++) {
                if (grid[y][x] != 0 || !nearBody(grid, x, y)) {
                    continue;
                }
                grid[y][x] = outline;
            }
        }
        Pixmap pm = new Pixmap(D_W, D_H, Pixmap.Format.RGBA8888);
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
        boolean inHead = hdx * hdx + hdy * hdy <= 46.0;          // 大头（Q 版约占体高 40%）
        if (inHead) {
            if (y <= 8.5 || (y <= 12.5 && x <= 6.5)) {
                return (y >= 7 && y <= 8 && x >= 8 && x <= 11) ? hairHi : hair;   // 头发+高光
            }
            if (y >= 11 && y < 12.4 && x >= 12 && x <= 14) {
                return eye;                                       // 闭眼（睡眠横线）
            }
            if (x >= 15 && x <= 16 && y >= 11.5 && y <= 13) {
                return skinShade;                                 // 小鼻子
            }
            if (x >= 12.5 && x <= 14 && y >= 13.5 && y <= 14.5) {
                return blush;                                     // 腮红
            }
            return skin;
        }
        if (y >= 15.5 && y < 17 && x >= 8.5 && x <= 11.5) {
            return skin;                                          // 脖颈
        }
        if (y >= 17 && y < 28) {
            int x0 = y < 19 ? 7 : y < 24 ? 6 : 5;                 // 肩部收窄、袍摆外扩
            int x1 = y < 19 ? 12 : y < 24 ? 13 : 14;
            if (x >= x0 && x <= x1) {
                if (y >= 17 && y < 18.4 && x >= 8 && x <= 12) {
                    return robeL;                                 // 领口
                }
                if (y >= 26.6) {
                    return robeD;                                 // 袍摆底边
                }
                return x < 8.5 ? robeD : robe;
            }
        }
        if (x >= 12 && x <= 14.5 && y >= 19 && y < 25) {
            return arm;                                           // 前臂
        }
        if (Math.pow(x - 13.5, 2) + Math.pow(y - 25.6, 2) <= 3.2) {
            return skin;                                          // 小手
        }
        if (y >= 28 && y < 38.5) {
            if (x >= 8.5 && x <= 12) {
                return pants;                                     // 前腿
            }
            if (x >= 5 && x <= 8) {
                return pantsD;                                    // 后腿（暗一档）
            }
        }
        if (y >= 38.5 && y < 41) {
            if (x >= 8.5 && x <= 14.5) {
                return shoe;                                      // 前鞋
            }
            if (x >= 4 && x <= 8) {
                return robeD;                                     // 后鞋
            }
        }
        return 0;
    }

    /* ---------------- 工具 ---------------- */

    private static void put(Pixmap pm, int x, int y, int rgb) {
        pm.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, 1f);
        pm.drawPixel(x, y);
    }

    private static int shade(int rgb, double factor) {
        int r = (int) Math.min(255, Math.max(0, ((rgb >> 16) & 0xFF) * factor));
        int g = (int) Math.min(255, Math.max(0, ((rgb >> 8) & 0xFF) * factor));
        int b = (int) Math.min(255, Math.max(0, (rgb & 0xFF) * factor));
        return r << 16 | g << 8 | b;
    }
}
