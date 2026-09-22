package com.cavedream.core.world;

import java.util.Random;

/**
 * L1 浅梦箱庭生成器（GDD §2.3 纵向三段式的第一个落地）。
 * 种子确定性：同 seed 同尺寸必得同一世界（"随机但持久"的底层保证）。
 *
 * 三段式（y 从下到上）：
 *   底部 深层     —— 石头 + 洞穴 + 沉眠矿
 *   中部 生态层   —— 地表草/土 + 树，探索建造主舞台
 *   顶部 梦缘     —— 开阔天空，中央出生点
 *   四周         —— 梦之雾软墙（2 格厚）
 */
public final class LayerGenerator {

    public static final int L1_WIDTH = 256;
    public static final int L1_HEIGHT = 192;
    private static final int FOG_BORDER = 2;

    private LayerGenerator() {
    }

    public static LayerWorld shallowGarden(long seed) {
        return shallowGarden(seed, L1_WIDTH, L1_HEIGHT);
    }

    public static LayerWorld shallowGarden(long seed, int width, int height) {
        LayerWorld world = new LayerWorld(width, height);
        Random rnd = new Random(seed);

        carveFogBorder(world, width, height);

        // 地表高度：中心基准 + 受限随机游走
        int base = (int) (height * 0.55);
        int[] surface = new int[width];
        int h = base;
        for (int x = 0; x < width; x++) {
            if (x > FOG_BORDER) {
                h += rnd.nextInt(3) - 1;
                h = Math.max(base - 8, Math.min(base + 8, h));
            }
            surface[x] = h;
        }

        int stoneLine = (int) (height * 0.32);   // 深层/生态层分界（近似）
        for (int x = FOG_BORDER; x < width - FOG_BORDER; x++) {
            for (int y = FOG_BORDER; y <= surface[x]; y++) {
                if (y < stoneLine) {
                    world.setBlock(x, y, BlockType.STONE);
                } else if (y == surface[x]) {
                    world.setBlock(x, y, BlockType.GRASS);
                } else {
                    world.setBlock(x, y, BlockType.DIRT);
                }
            }
        }

        digCaves(world, rnd, width, height, stoneLine);
        scatterOre(world, rnd, width, stoneLine);
        plantTrees(world, rnd, width, surface);

        // 出生点：水平中央，草面上一格空气
        int spawnX = width / 2;
        world.setSpawn(spawnX, surface[spawnX] + 1);
        return world;
    }

    private static void carveFogBorder(LayerWorld world, int width, int height) {
        for (int x = 0; x < width; x++) {
            for (int b = 0; b < FOG_BORDER; b++) {
                world.setBlock(x, b, BlockType.FOG);
                world.setBlock(x, height - 1 - b, BlockType.FOG);
            }
        }
        for (int y = 0; y < height; y++) {
            for (int b = 0; b < FOG_BORDER; b++) {
                world.setBlock(b, y, BlockType.FOG);
                world.setBlock(width - 1 - b, y, BlockType.FOG);
            }
        }
    }

    /** 洞穴：若干随机游走蠕虫在深层掏空。 */
    private static void digCaves(LayerWorld world, Random rnd, int width, int height, int stoneLine) {
        int worms = width / 24;
        for (int i = 0; i < worms; i++) {
            double cx = FOG_BORDER + 3 + rnd.nextInt(width - 2 * FOG_BORDER - 6);
            double cy = FOG_BORDER + 2 + rnd.nextInt(Math.max(4, stoneLine - FOG_BORDER - 4));
            double dir = rnd.nextDouble() * Math.PI * 2;
            int len = 40 + rnd.nextInt(80);
            for (int s = 0; s < len; s++) {
                int tx = (int) cx;
                int ty = (int) cy;
                BlockType cur = world.blockAt(tx, ty);
                if (cur == BlockType.STONE || cur == BlockType.DIRT) {
                    world.setBlock(tx, ty, BlockType.AIR);
                    if (rnd.nextBoolean() && world.blockAt(tx, ty + 1) == BlockType.STONE) {
                        world.setBlock(tx, ty + 1, BlockType.AIR);   // 洞穴加高一点
                    }
                }
                dir += rnd.nextDouble() * 1.2 - 0.6;
                cx += Math.cos(dir);
                cy += Math.sin(dir) * 0.7;
                if (cx <= FOG_BORDER || cx >= width - FOG_BORDER - 1
                        || cy <= FOG_BORDER || cy >= stoneLine + 6) {
                    break;   // 不啃穿雾墙、不掏穿地表
                }
            }
        }
    }

    private static void scatterOre(LayerWorld world, Random rnd, int width, int stoneLine) {
        for (int x = FOG_BORDER; x < width - FOG_BORDER; x++) {
            for (int y = FOG_BORDER; y < stoneLine; y++) {
                if (world.blockAt(x, y) == BlockType.STONE && rnd.nextDouble() < 0.05) {
                    world.setBlock(x, y, BlockType.DEEP_SEED);
                }
            }
        }
    }

    private static void plantTrees(LayerWorld world, Random rnd, int width, int[] surface) {
        int spawnX = width / 2;
        int x = FOG_BORDER + 6 + rnd.nextInt(8);
        while (x < width - FOG_BORDER - 6) {
            if (Math.abs(x - spawnX) < 5) {   // 避让出生点
                x += 6;
                continue;
            }
            int ground = surface[x];
            int trunk = 3 + rnd.nextInt(3);
            for (int t = 1; t <= trunk; t++) {
                world.setBlock(x, ground + t, BlockType.WOOD);
            }
            int top = ground + trunk;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (world.blockAt(x + dx, top + dy) == BlockType.AIR) {
                        world.setBlock(x + dx, top + dy, BlockType.LEAF);
                    }
                }
            }
            x += 14 + rnd.nextInt(12);
        }
    }
}
