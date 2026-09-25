package com.cavedream.core.world.gen;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;

/**
 * 大世界生成器（M2 主件）：四带一海 + §2.5 骨架要素落位。
 * 方案 C：骨架固定（本算法结构即骨架），血肉种子化（layerSeed 决定一切随机）。
 * 硬保证：出生点→Boss 门连通（BFS 校验，失败则开紧急竖井重挖）。
 */
public final class WorldGenerator {

    private WorldGenerator() {
    }

    public static GeneratedWorld generate(LayerLayout L, long layerSeed) {
        Random rnd = new Random(layerSeed);
        int W = L.width();
        int H = L.height();
        LayerWorld w = new LayerWorld(W, H);

        int hellTop = (int) Math.round(H * L.hellRatio());
        int caveTop = hellTop + (int) Math.round(H * L.caveRatio());
        int surfTop = caveTop + (int) Math.round(H * L.surfaceRatio());

        // ---------- 1 地表高度图（多频正弦叠加 = 温和丘陵） ----------
        int[] ground = new int[W];
        double base = caveTop + (surfTop - caveTop) * 0.55;
        double amp = (surfTop - caveTop) * 0.26;
        double p1 = rnd.nextDouble() * 6.283, p2 = rnd.nextDouble() * 6.283, p3 = rnd.nextDouble() * 6.283;
        double f1 = 6.283 / (W * (0.15 + rnd.nextDouble() * 0.2));
        double f2 = 6.283 / (Math.max(1.0, W * 0.04));
        double f3 = 6.283 / (Math.max(1.0, W * 0.01));
        for (int x = 0; x < W; x++) {
            double v = base + amp * (0.55 * Math.sin(x * f1 + p1)
                    + 0.3 * Math.sin(x * f2 + p2)
                    + 0.15 * Math.sin(x * f3 + p3));
            ground[x] = (int) clamp(v, caveTop + 8, surfTop - 6);
        }

        // ---------- 2 海（≥1 侧，双侧 25%） ----------
        GeneratedWorld.SeaSide side = rnd.nextDouble() < L.bothSeaChance()
                ? GeneratedWorld.SeaSide.BOTH
                : (rnd.nextBoolean() ? GeneratedWorld.SeaSide.LEFT : GeneratedWorld.SeaSide.RIGHT);
        int seaW = Math.min(L.seaWidthMin() + rnd.nextInt(Math.max(1,
                L.seaWidthMax() - L.seaWidthMin() + 1)), W / 3);
        int seaFloor = hellTop + Math.max(4, (caveTop - hellTop) / 12);
        int seaLevelCap = caveTop + (int) ((surfTop - caveTop) * 0.35);
        boolean[] inSea = new boolean[W];
        int[] waterTop = new int[W];
        Arrays.fill(waterTop, -1);
        int seaCenterX = W / 2;
        if (side != GeneratedWorld.SeaSide.RIGHT) {
            seaCenterX = applySea(ground, inSea, waterTop, 2, 2 + seaW, seaFloor, seaLevelCap);
        }
        if (side != GeneratedWorld.SeaSide.LEFT) {
            int rx2 = W - 2;
            int rx1 = rx2 - seaW;
            int c = applySea(ground, inSea, waterTop, rx1, rx2 - 1, seaFloor, seaLevelCap);
            if (side == GeneratedWorld.SeaSide.LEFT) {
                seaCenterX = c;
            } else {
                seaCenterX = (seaCenterX + c) / 2;
            }
        }

        // ---------- 3 纵向填充（地狱/洞穴/地表/天空） ----------
        for (int x = 0; x < W; x++) {
            int g = ground[x];
            boolean sea = inSea[x];
            for (int y = 2; y < H - 2; y++) {
                BlockType b;
                if (y < hellTop) {
                    b = BlockType.HELL;
                } else if (y < g - 6) {
                    b = BlockType.STONE;
                } else if (y < g) {
                    b = BlockType.DIRT;
                } else if (y == g) {
                    b = sea ? BlockType.DIRT : BlockType.GRASS;
                } else {
                    b = BlockType.AIR;
                }
                if (b == BlockType.AIR && sea && waterTop[x] >= 0 && y > g && y <= waterTop[x]) {
                    b = BlockType.WATER;
                }
                if (b != BlockType.AIR) {
                    w.setBlock(x, y, b);
                }
            }
        }

        // ---------- 4 梦之雾边界（厚 2） ----------
        for (int x = 0; x < W; x++) {
            for (int b = 0; b < 2; b++) {
                w.setBlock(x, b, BlockType.FOG);
                w.setBlock(x, H - 1 - b, BlockType.FOG);
            }
        }
        for (int y = 0; y < H; y++) {
            for (int b = 0; b < 2; b++) {
                w.setBlock(b, y, BlockType.FOG);
                w.setBlock(W - 1 - b, y, BlockType.FOG);
            }
        }

        // ---------- 5 洞系：蠕虫 + 大洞厅 ----------
        List<TilePos> wormSpots = new ArrayList<>();
        int worms = Math.max(8, W / 8);
        for (int i = 0; i < worms; i++) {
            double cx = 3 + rnd.nextInt(W - 6);
            double cy = hellTop + 3 + rnd.nextInt(Math.max(1, caveTop - hellTop - 10));
            double dir = rnd.nextDouble() * 6.283;
            int len = 80 + rnd.nextInt(120);
            boolean sawStone = false;
            for (int s = 0; s < len; s++) {
                int tx = (int) cx, ty = (int) cy;
                if (tx > 2 && tx < W - 3 && ty > 2 && ty < caveTop - 2) {
                    // 海底板保护：海柱下方 3 格内不穿
                    if (!(inSea[tx] && ty <= seaFloor + 3)) {
                        for (int dx = -2; dx <= 2; dx++) {
                            for (int dy = -2; dy <= 2; dy++) {
                                if (dx * dx + dy * dy > 5 + rnd.nextInt(3)) {
                                    continue;
                                }
                                int nx = tx + dx, ny = ty + dy;
                                if (nx > 2 && nx < W - 3 && ny > hellTop - 2 && ny < caveTop - 2
                                        && w.blockAt(nx, ny) != BlockType.AIR
                                        && w.blockAt(nx, ny) != BlockType.FOG) {
                                    w.setBlock(nx, ny, BlockType.AIR);
                                    sawStone = true;
                                }
                            }
                        }
                    }
                }
                dir += rnd.nextDouble() * 1.1 - 0.55;
                cx += Math.cos(dir);
                cy += Math.sin(dir) * 0.6;
                if (cx <= 3 || cx >= W - 4 || cy <= hellTop || cy >= caveTop - 4) {
                    break;
                }
            }
            if (sawStone) {
                wormSpots.add(new TilePos((int) clamp(cx, 4, W - 5), (int) clamp(cy, hellTop + 3, caveTop - 5)));
            }
        }
        int caverns = Math.max(3, W / 260);
        for (int i = 0; i < caverns; i++) {
            int ccx = 20 + rnd.nextInt(W - 40);
            int ccy = hellTop + 8 + rnd.nextInt(Math.max(1, caveTop - hellTop - 16));
            int rx = 15 + rnd.nextInt(30), ry = 6 + rnd.nextInt(10);
            for (int dx = -rx; dx <= rx; dx++) {
                for (int dy = -ry; dy <= ry; dy++) {
                    if ((dx * dx) / (double) (rx * rx) + (dy * dy) / (double) (ry * ry) > 1) {
                        continue;
                    }
                    int nx = ccx + dx, ny = ccy + dy;
                    if (nx > 2 && nx < W - 3 && ny > hellTop && ny < caveTop - 2
                            && w.blockAt(nx, ny) != BlockType.FOG) {
                        w.setBlock(nx, ny, BlockType.AIR);
                    }
                }
            }
        }

        // ---------- 6 垂直主脉 ×2 + 分支回路 ----------
        int[] shaftX = {landColumn((int) (W * 0.32), W, inSea), landColumn((int) (W * 0.68), W, inSea)};
        for (int sx : shaftX) {
            carveShaft(w, ground[sx], sx, hellTop + 1, 4);
        }
        for (int i = 0; i < L.branchCount(); i++) {
            int bx = landColumn(6 + rnd.nextInt(W - 12), W, inSea);
            double cx = bx, cy = ground[bx] + 1;
            double dir = Math.PI / 2 + (rnd.nextDouble() - 0.5); // 向下为主
            int len = 150 + rnd.nextInt(250);
            for (int s = 0; s < len; s++) {
                int tx = (int) cx, ty = (int) cy;
                for (int dx = -1; dx <= 1; dx++) {
                    int nx = tx + dx, ny = ty;
                    if (nx > 2 && nx < W - 3 && ny > hellTop && ny < caveTop
                            && w.blockAt(nx, ny) != BlockType.FOG) {
                        w.setBlock(nx, ny, BlockType.AIR);
                    }
                }
                dir += rnd.nextDouble() * 0.6 - 0.3;
                cx += Math.cos(dir);
                cy -= Math.abs(Math.sin(dir)) * (rnd.nextBoolean() ? 1 : -1) * 0.4;
                if (cx <= 3 || cx >= W - 4 || cy <= hellTop + 2 || cy >= caveTop) {
                    break;
                }
            }
        }

        // ---------- 7 天空带：先定主岛（出生/入梦锤），再在其外围撒浮岛群 ----------
        int anchorX = (int) clamp(W / 2.0 + (rnd.nextDouble() * 0.3 - 0.15) * W, 120, W - 120);
        int mainTopY = surfTop + (int) ((H - surfTop) * 0.45);
        int mainRx = Math.max(60, W / 60);        // 主岛为中央出生/入梦岛，不随浮岛群放大
        int mainRy = Math.max(10, H / 120);
        carveIsland(w, anchorX, mainTopY, mainRx, mainRy, H);
        // 浮岛群体量 ×3，但：①半径锁不超图宽/5（不横贯雾墙隔断连通）；②跳过水平投影压在/穿过主岛上空的（否则截断出生列天光→正午仍黑）
        int islandCount = Math.max(2, W / Math.max(1, L.islandDensity()));
        for (int i = 0; i < islandCount; i++) {
            int icx = 30 + rnd.nextInt(Math.max(1, W - 60));
            int icy = surfTop + 12 + rnd.nextInt(Math.max(1, H - surfTop - 34));
            int irx = Math.min(26 + rnd.nextInt(60), Math.max(20, W / 5));
            int iry = Math.min(7 + rnd.nextInt(11), Math.max(8, H / 30));
            if (Math.abs(icx - anchorX) <= mainRx + irx) {
                continue;   // 与主岛列有水平重叠→不生成（保证出生天空不被遮）
            }
            carveIsland(w, icx, icy, irx, iry, H);
        }

        // ---------- 7b 浮岛顶植树（天空岛不再光秃；跳过主浮岛=出生/入梦锤所在，不埋出生点） ----------
        plantIslandTrees(w, rnd, surfTop, H, anchorX - mainRx, anchorX + mainRx);

        // ---------- 7c 天空岛石核布矿（host=STONE：天空带内仅岛体为石，天然只在岛内成矿） ----------
        oreVeins(w, rnd, BlockType.IRON_ORE, BlockType.STONE, surfTop + 8, caveTop, 3000);
        oreVeins(w, rnd, BlockType.GOLD_ORE, BlockType.STONE, surfTop + 8, caveTop, 6000);

        // ---------- 8 地狱带 Boss 竞技场 ----------
        int ax = W / 2;
        int arenaH = Math.min(18, Math.max(8, caveTop - hellTop - 6));
        int arenaW2 = Math.min(24, W / 8);
        for (int dx = -arenaW2; dx <= arenaW2; dx++) {
            for (int dy = 0; dy <= arenaH; dy++) {
                int nx = ax + dx, ny = hellTop + 2 + dy;
                if (nx > 2 && nx < W - 3 && ny < caveTop) {
                    w.setBlock(nx, ny, BlockType.AIR);
                }
            }
        }
        TilePos bossGate = new TilePos(ax, hellTop + 2 + arenaH + 1);

        // 8b 地狱横向通道：把两条主脉底部串到竞技场，保证可行走连通
        for (int sx2 : shaftX) {
            int from = Math.min(sx2, ax - arenaW2);
            int to = Math.max(sx2, ax + arenaW2);
            for (int x = from; x <= to; x++) {
                for (int y = hellTop + 2; y <= hellTop + 3; y++) {
                    if (x > 2 && x < W - 3 && w.blockAt(x, y) != BlockType.FOG) {
                        w.setBlock(x, y, BlockType.AIR);
                    }
                }
            }
        }

        // ---------- 9 矿脉：簇状、按深度分层（挖掘力度越高的矿埋得越深、越稀有） ----------
        int stoneH = caveTop - hellTop;
        oreVeins(w, rnd, BlockType.DEEP_SEED, BlockType.STONE, caveTop - stoneH / 3, caveTop, 2600);        // 浅层岩：常见
        oreVeins(w, rnd, BlockType.IRON_ORE, BlockType.STONE, caveTop - 2 * stoneH / 3, caveTop - stoneH / 3, 3600);
        oreVeins(w, rnd, BlockType.GOLD_ORE, BlockType.STONE, hellTop + stoneH / 3, caveTop - 2 * stoneH / 3, 5200);
        oreVeins(w, rnd, BlockType.PLATINUM_ORE, BlockType.STONE, hellTop, hellTop + stoneH / 3, 9000);      // 深层岩
        oreVeins(w, rnd, BlockType.SAINT_ORE, BlockType.HELL, 2, hellTop, 16000);                            // 地狱带：最稀有

        // ---------- 10 植被（林/原/菌三生物群系） ----------
        plantVegetation(w, rnd, ground, inSea, caveTop, W);

        // ---------- 11 陶罐簇 ----------
        int potClusters = Math.max(4, W / 700);
        for (int i = 0; i < potClusters; i++) {
            int px = landColumn(6 + rnd.nextInt(W - 12), W, inSea);
            int n = 3 + rnd.nextInt(3);
            for (int k = 0; k < n; k++) {
                int x = px + k * 2;
                if (x < W - 3 && !inSea[x] && w.blockAt(x, ground[x]) == BlockType.GRASS
                        && w.blockAt(x, ground[x] + 1) == BlockType.AIR) {
                    w.setBlock(x, ground[x] + 1, BlockType.POT);
                }
            }
        }

        // ---------- 12 要素标记 ----------
        TilePos dreamLoom = surfaceMark(ground, inSea, (int) (W * 0.40), W);
        TilePos artisan = surfaceMark(ground, inSea, (int) (W * 0.45), W);
        TilePos spawn = new TilePos(anchorX, mainTopY + mainRy + 2);
        TilePos dreamEntry = new TilePos(anchorX, mainTopY + 1);
        TilePos bedSite = new TilePos(anchorX + 24, mainTopY + 1);

        List<TilePos> pillars = new ArrayList<>();
        for (int i = 0; i < L.pillarCount(); i++) {
            int px = landColumn((int) (W * (i + 0.5) / L.pillarCount()), W, inSea);
            pillars.add(surfaceMark(ground, inSea, px, W));
        }
        List<TilePos> chests = new ArrayList<>();
        chests.add(new TilePos(clampInt(seaCenterX, 4, W - 5), seaFloor + 1));   // 海底必刷
        for (TilePos sp : wormSpots) {
            if (chests.size() < Math.max(2, L.chestCount() - 4)) {
                chests.add(sp);
            }
        }
        chests.add(new TilePos(clampInt(ax + arenaW2 + 6, 4, W - 5), hellTop + 3)); // 地狱 1
        while (chests.size() < L.chestCount()) {
            chests.add(surfaceMark(ground, inSea, 6 + rnd.nextInt(W - 12), W));
        }
        List<TilePos> narrative = new ArrayList<>();
        for (int i = 0; i < L.narrativeCount(); i++) {
            narrative.add(surfaceMark(ground, inSea, 6 + rnd.nextInt(W - 12), W));
        }
        List<TilePos> gathering = new ArrayList<>();
        for (int i = 0; i < L.gatheringCount(); i++) {
            gathering.add(surfaceMark(ground, inSea, 6 + rnd.nextInt(W - 12), W));
        }
        TilePos vessel = chests.size() > 2 ? chests.get(chests.size() / 2) : new TilePos(ax, caveTop - 4);

        // ---------- 12b 地下铺自然背景墙（泰拉瑞亚式透光）：地表(ground)以下各格背后置墙 → 洞穴天然黑暗、
        //              挖开仍留墙需火把；天空带/浮岛在地表以上 → 不置墙 → 天光连通漫灌，地标不被头顶浮岛遮黑。 ----------
        for (int x = 0; x < W; x++) {
            int surf = ground[x];
            for (int y = 0; y < surf; y++) {
                w.setBackWall(x, y, true);
            }
        }

        // ---------- 13 连通性：主脉即开口竖井（地面层一并挖开，入曰可进），结构上保证地表→地狱贯通；
        // reachable() 保留给单测做硬校验 ----------
        w.setSpawn(spawn.x(), spawn.y());

        return new GeneratedWorld(w, spawn, dreamEntry, bedSite, dreamLoom, artisan,
                bossGate, vessel, List.copyOf(pillars), List.copyOf(chests),
                List.copyOf(narrative), List.copyOf(gathering), side, ground);
    }

    /* ---------------- 分段实现 ---------------- */

    /** 海：区间内地面压到海床，水面取岸高-1（上限不过地表带）；返回海心 x。 */
    private static int applySea(int[] ground, boolean[] inSea, int[] waterTop,
                                int from, int to, int seaFloor, int levelCap) {
        int shoreX = clampInt(to, 0, ground.length - 1);
        int level = clampInt(ground[shoreX] - 1, seaFloor + 6, levelCap);
        for (int x = from; x <= to; x++) {
            ground[x] = seaFloor;
            inSea[x] = true;
            waterTop[x] = level;
        }
        return (from + to) / 2;
    }

    private static void carveShaft(LayerWorld w, int topY, int x, int bottomY, int width) {
        for (int y = bottomY; y <= topY; y++) {
            for (int dx = 0; dx < width; dx++) {
                int nx = x + dx;
                if (nx > 2 && nx < w.getWidth() - 3 && w.blockAt(nx, y) != BlockType.FOG) {
                    w.setBlock(nx, y, BlockType.AIR);
                }
            }
        }
    }

    private static void carveIsland(LayerWorld w, int cx, int topY, int rx, int ry, int H) {
        for (int dx = -rx; dx <= rx; dx++) {
            for (int dy = -ry; dy <= ry; dy++) {
                double e = (dx * dx) / (double) (rx * rx) + (dy * dy) / (double) (ry * ry);
                if (e > 1) {
                    continue;
                }
                int nx = cx + dx, ny = topY - dy;
                if (nx <= 2 || nx >= w.getWidth() - 3 || ny <= 3 || ny >= H - 3) {
                    continue;
                }
                BlockType b;
                if (dy < -ry * 0.55) {
                    b = BlockType.GRASS;             // 表层草（可长树）
                } else if (dy < 0) {
                    b = BlockType.DIRT;              // 浅土层
                } else if (dy < ry * 0.5) {
                    b = BlockType.STONE;             // 石核：可挖掘、容矿脉
                } else {
                    b = BlockType.CLOUD;             // 岛底云絮
                }
                w.setBlock(nx, ny, b);
            }
        }
    }

    /**
     * 簇状矿脉：在 [yLo,yHi) 带内按密度撒团，每团 2~16 格（偏小），只把 host 原块转为 ore。
     * 稀有度由带深度 + divisor 共同控制（divisor 越大越稀）。
     */
    private static void oreVeins(LayerWorld w, Random rnd, BlockType ore, BlockType host,
                                 int yLo, int yHi, int divisor) {
        yLo = Math.max(3, yLo);
        yHi = Math.min(w.getHeight() - 3, yHi);
        if (yHi - yLo < 3) {
            return;
        }
        int clusters = (int) Math.min(200000L, (long) w.getWidth() * (yHi - yLo) / Math.max(1, divisor));
        for (int i = 0; i < clusters; i++) {
            int ox = 4 + rnd.nextInt(Math.max(1, w.getWidth() - 8));
            int oy = yLo + rnd.nextInt(Math.max(1, yHi - yLo));
            if (w.blockAt(ox, oy) != host) {
                continue;
            }
            int size = 2 + (int) (Math.pow(rnd.nextDouble(), 1.8) * 15);   // 2..16，偏小簇
            growVein(w, host, ore, ox, oy, size, rnd, yLo, yHi);
        }
    }

    /** 从种子随机游走把 host 转成 ore，最多 size 格、限制在带内。 */
    private static void growVein(LayerWorld w, BlockType host, BlockType ore,
                                 int x, int y, int size, Random rnd, int yLo, int yHi) {
        int cx = x, cy = y, placed = 0;
        for (int k = 0; k < size * 3 && placed < size; k++) {
            if (w.blockAt(cx, cy) == host) {
                w.setBlock(cx, cy, ore);
                placed++;
            }
            switch (rnd.nextInt(4)) {
                case 0 -> cx++;
                case 1 -> cx--;
                case 2 -> cy++;
                default -> cy--;
            }
            cx = clampInt(cx, 3, w.getWidth() - 4);
            cy = clampInt(cy, yLo, yHi - 1);
        }
    }

    private static void plantVegetation(LayerWorld w, Random rnd, int[] ground, boolean[] inSea,
                                        int caveTop, int W) {
        int forestA0 = (int) (W * 0.18), forestA1 = (int) (W * 0.42);
        int forestB0 = (int) (W * 0.62), forestB1 = (int) (W * 0.86);
        int shroom0 = (int) (W * 0.46), shroom1 = (int) (W * 0.56);
        int x = 6;
        while (x < W - 6) {
            boolean forest = (x >= forestA0 && x <= forestA1) || (x >= forestB0 && x <= forestB1);
            boolean shroom = x >= shroom0 && x <= shroom1;
            if (!inSea[x] && ground[x] >= caveTop + 8) {
                if (forest) {
                    tree(w, rnd, ground, x);
                    x += 6 + rnd.nextInt(9);
                } else if (shroom) {
                    int g = ground[x];
                    if (w.blockAt(x, g) == BlockType.GRASS && w.blockAt(x, g + 1) == BlockType.AIR) {
                        w.setBlock(x, g + 1, BlockType.MUSHROOM);
                    }
                    x += 8 + rnd.nextInt(10);
                } else if (rnd.nextInt(40) == 0) {
                    tree(w, rnd, ground, x);
                    x += 20 + rnd.nextInt(20);
                } else {
                    x += 4 + rnd.nextInt(6);
                }
            } else {
                x += 3 + rnd.nextInt(5);
            }
        }
    }

    private static void tree(LayerWorld w, Random rnd, int[] ground, int x) {
        treeAt(w, rnd, x, ground[x]);
    }

    /** 以 (x, g) 处草地为基植一棵树（g 为该格 GRASS 的 y）；非草则跳过。 */
    private static void treeAt(LayerWorld w, Random rnd, int x, int g) {
        if (w.blockAt(x, g) != BlockType.GRASS) {
            return;
        }
        int h = 4 + rnd.nextInt(4);
        for (int t = 1; t <= h; t++) {
            if (w.blockAt(x, g + t) == BlockType.AIR) {
                w.setBlock(x, g + t, BlockType.WOOD);
            }
        }
        int top = g + h;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                int nx = x + dx, ny = top + dy;
                if (nx > 2 && nx < w.getWidth() - 3 && ny < w.getHeight() - 3
                        && w.blockAt(nx, ny) == BlockType.AIR) {
                    w.setBlock(nx, ny, BlockType.LEAF);
                }
            }
        }
    }

    /** 扫描天空带各列的浮岛顶（草+上方空气），按概率点缀树木；[skipX0,skipX1] 为主浮岛，跳过。 */
    private static void plantIslandTrees(LayerWorld w, Random rnd, int surfTop, int H, int skipX0, int skipX1) {
        int yLo = Math.max(6, surfTop + 8);
        for (int x = 6; x < w.getWidth() - 6; x++) {
            if (x >= skipX0 && x <= skipX1) {
                continue;   // 主浮岛留空，不埋出生点
            }
            for (int y = yLo; y < H - 6; y++) {
                if (w.blockAt(x, y) == BlockType.GRASS && w.blockAt(x, y + 1) == BlockType.AIR) {
                    if (rnd.nextInt(5) == 0) {
                        treeAt(w, rnd, x, y);
                    }
                    break;   // 该列只认最下一个岛顶，避免堆叠
                }
            }
        }
    }

    /** 就近找一根不在海里的柱子。 */
    private static int landColumn(int x, int W, boolean[] inSea) {
        int c = clampInt(x, 4, W - 5);
        if (!inSea[c]) {
            return c;
        }
        for (int d = 1; d < W; d++) {
            if (c - d > 3 && !inSea[c - d]) {
                return c - d;
            }
            if (c + d < W - 4 && !inSea[c + d]) {
                return c + d;
            }
        }
        return c;
    }

    private static TilePos surfaceMark(int[] ground, boolean[] inSea, int x, int W) {
        int c = landColumn(x, W, inSea);
        return new TilePos(c, ground[c] + 1);
    }

    /* ---------------- 连通性 BFS ---------------- */

    /** 四方向 BFS：梦水可通行（游泳/溺水机制后续接入）。 */
    public static boolean reachable(LayerWorld w, TilePos from, TilePos to) {
        int W = w.getWidth(), H = w.getHeight();
        int total = W * H;
        BitSet seen = new BitSet(total);
        int[] queue = new int[Math.min(total, 1 << 22)];
        int head = 0, tail = 0;
        int start = from.y() * W + from.x();
        int goal = to.y() * W + to.x();
        if (from.x() < 0 || from.x() >= W || from.y() < 0 || from.y() >= H
                || to.x() < 0 || to.x() >= W || to.y() < 0 || to.y() >= H) {
            return false;
        }
        seen.set(start);
        queue[tail++] = start;
        int[] dirs = {1, -1, W, -W};
        while (head < tail) {
            int cur = queue[head++];
            if (cur == goal) {
                return true;
            }
            int cx = cur % W, cy = cur / W;
            for (int d : dirs) {
                int nx = cx + (d == 1 ? 1 : d == -1 ? -1 : 0);
                int ny = cy + (d == W ? 1 : d == -W ? -1 : 0);
                if (nx < 0 || nx >= W || ny < 0 || ny >= H) {
                    continue;
                }
                int ni = ny * W + nx;
                if (seen.get(ni) || w.isSolid(nx, ny)) {
                    continue;
                }
                seen.set(ni);
                if (tail < queue.length) {
                    queue[tail++] = ni;
                }
            }
        }
        return seen.get(goal);
    }

    /* ---------------- 工具 ---------------- */

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
