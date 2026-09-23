package com.cavedream.core.world.gen;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;

/**
 * 手搭测试床（M3 物品渲染验收用）：不依赖 {@link WorldGenerator}，
 * 一屏内摆出每种材料的可见样例（草面+草唇、水潭、泥/石层、矿、木/叶树、幻菇、云絮、梦滓、陶罐、雾界）。
 * 用途：把 DB 物品图集逐项渲染出来核对，验证“图片来自表”而非程序生成。
 */
public record TestBed(LayerWorld world, int[] surfaceY, int spawnX, int spawnY) {

    private static final int W = 96;
    private static final int H = 48;

    public static TestBed build() {
        LayerWorld w = new LayerWorld(W, H);
        int ground = 18;   // 地表实心顶面所在 y（其上是空气）

        // 地下分层：底部梦滓，往上石头夹沉眠矿，再上泥土，顶行草
        for (int x = 0; x < W; x++) {
            for (int y = 0; y < H; y++) {
                if (y == 0 || y == H - 1 || x == 0 || x == W - 1) {
                    w.setBlock(x, y, BlockType.FOG);            // 雾界围墙
                } else if (y < 3) {
                    w.setBlock(x, y, BlockType.HELL);           // 梦滓（地狱带样例）
                } else if (y <= ground - 6) {
                    w.setBlock(x, y, BlockType.STONE);          // 石头
                } else if (y < ground) {
                    w.setBlock(x, y, BlockType.DIRT);           // 泥土
                } else if (y == ground) {
                    w.setBlock(x, y, BlockType.GRASS);          // 草面（触发草唇）
                }
                // ground 之上留空 = 天空
            }
        }

        // 沉眠矿脉（石头里嵌一横排）
        for (int x = 10; x < 18; x++) {
            w.setBlock(x, 7, BlockType.DEEP_SEED);
        }
        // 陶罐（放地上，看可砸样例）
        w.setBlock(30, ground + 1, BlockType.POT);
        // 幻菇丛
        for (int x = 34; x <= 36; x++) {
            w.setBlock(x, ground + 1, BlockType.MUSHROOM);
        }
        // 一棵树：树干 + 树叶冠
        int tx = 44;
        for (int y = ground + 1; y <= ground + 5; y++) {
            w.setBlock(tx, y, BlockType.WOOD);
        }
        for (int x = tx - 2; x <= tx + 2; x++) {
            for (int y = ground + 6; y <= ground + 8; y++) {
                if (w.blockAt(x, y) == BlockType.AIR) {
                    w.setBlock(x, y, BlockType.LEAF);
                }
            }
        }
        // 水潭：挖一个 2 深坑灌梦水
        int wx = 60;
        for (int x = wx; x < wx + 8; x++) {
            w.setBlock(x, ground, BlockType.AIR);
            w.setBlock(x, ground - 1, BlockType.WATER);
            w.setBlock(x, ground - 2, BlockType.WATER);
            if (x == wx || x == wx + 7) {
                w.setBlock(x, ground - 1, BlockType.STONE);
                w.setBlock(x, ground - 2, BlockType.STONE);
            }
        }
        // 云絮浮岛（空中一块）
        for (int x = 70; x < 80; x++) {
            w.setBlock(x, ground + 10, BlockType.CLOUD);
        }

        // 每列地表高度（供背景画布天空/深度分层）
        int[] surfaceY = new int[W];
        for (int x = 0; x < W; x++) {
            int sy = ground;
            for (int y = H - 2; y > 0; y--) {
                if (w.blockAt(x, y).solid()) {
                    sy = y;
                    break;
                }
            }
            surfaceY[x] = sy;
        }

        int spawnX = 20;
        int spawnY = surfaceY[spawnX] + 1;   // 草面上方一格空气
        w.setSpawn(spawnX, spawnY);
        return new TestBed(w, surfaceY, spawnX, spawnY);
    }
}
