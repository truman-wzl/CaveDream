package com.cavedream.core.world.gen;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;

import java.util.Random;

/**
 * 村庄生成器（纯逻辑、可单测）：在出生点附近平整地表上盖若干栋<b>人字顶村舍</b>——
 * 夯平地基、木墙+顶梁、中央门、两侧窗、LEAF 茅草坡顶（带屋檐外扩）、石烟囱、屋内幻菇照明。
 * 由 seed 确定性生成，读档可复现。返回"构梦者"落点（首栋门前的空气格）。
 */
public final class VillageBuilder {

    private VillageBuilder() {
    }

    /** 在 world 上、出生点附近建村庄；返回构梦者落点 {tileX, tileY}（门前空气）。 */
    public static int[] build(LayerWorld world, int spawnX, int spawnY, long seed) {
        Random rnd = new Random(seed ^ 0x5EEDL);
        int w = world.getWidth();
        int huts = 3 + rnd.nextInt(3);                 // 3~5 栋
        int cursor = Math.max(4, spawnX - 24);
        int guideX = spawnX;
        int guideY = spawnY;
        boolean guideSet = false;
        for (int i = 0; i < huts && cursor < w - 20; i++) {
            int hw = 15 + 2 * rnd.nextInt(2);          // 外宽 15 或 17（内部净宽 13/15，≥12）
            int wallH = 9 + rnd.nextInt(2);            // 墙高 9~10（内部净高 8~9，≥8）
            int peak = 4 + rnd.nextInt(2);             // 人字顶高 4~5
            int ground = surfaceY(world, cursor + hw / 2);
            if (ground > 2 && ground < world.getHeight() - (wallH + peak + 4)) {
                buildCottage(world, cursor, ground, hw, wallH, peak, rnd);
                if (!guideSet) {
                    guideX = cursor - 2;                 // 首栋门前（左墙外）
                    guideY = ground + 1;
                    guideSet = true;
                }
            }
            cursor += hw + 8 + rnd.nextInt(8);         // 屋间留空地/路
        }
        return new int[]{guideX, guideY};
    }

    /** 盖一栋村舍：base=地表实心行；墙占 base+1..base+wallH，坡顶再向上 peak。 */
    private static void buildCottage(LayerWorld world, int x0, int base, int w, int wallH, int peak, Random rnd) {
        int top = base + wallH;                        // 顶梁行
        int center = x0 + w / 2;
        // 1) 夯平地基：屋脚外扩 1 格，削平上方、夯实下方，铺木地板
        for (int x = x0 - 1; x <= x0 + w; x++) {
            for (int y = base + 1; y <= top + peak + 1; y++) {
                setSafe(world, x, y, BlockType.AIR);   // 清出建筑空间（削小山）
            }
            for (int y = base - 3; y < base; y++) {    // 填平凹地
                if (world.inBounds(x, y) && !world.isSolid(x, y)) {
                    setSafe(world, x, y, BlockType.DIRT);
                }
            }
            setSafe(world, x, base, BlockType.WOOD);   // 木地板
        }
        // 2) 木墙 + 顶梁（内部留空）
        for (int x = x0; x < x0 + w; x++) {
            for (int y = base + 1; y <= top; y++) {
                boolean edge = (x == x0 || x == x0 + w - 1 || y == top);
                setSafe(world, x, y, edge ? BlockType.WOOD : BlockType.AIR);
            }
        }
        // 2.5) 屋内贴满木背景墙（非实体、可穿行；合法房屋“贴满墙”要件）
        for (int x = x0 + 1; x <= x0 + w - 2; x++) {
            for (int y = base + 1; y <= top - 1; y++) {
                setSafe(world, x, y, BlockType.WOOD_WALL);
            }
        }
        // 3) 两端侧墙开门（左墙 x0、右墙 x0+w-1，各 3 格高、可穿过的木门）
        for (int dy = 1; dy <= 3; dy++) {
            setSafe(world, x0, base + dy, BlockType.DOOR);
            setSafe(world, x0 + w - 1, base + dy, BlockType.DOOR);
        }
        // 4) 两侧窗（2 格高，透光的空气洞）
        int winY = base + wallH - 3;
        for (int wx : new int[]{x0 + 3, x0 + w - 4}) {
            setSafe(world, wx, winY, BlockType.AIR);
            setSafe(world, wx, winY + 1, BlockType.AIR);
        }
        // 5) 人字茅草顶（LEAF），每列随距中心升高，屋檐外扩 1、厚 2
        for (int x = x0 - 1; x <= x0 + w; x++) {
            int dist = Math.abs(x - center);
            int roofY = top + Math.max(0, peak - dist / 2);
            setSafe(world, x, roofY, BlockType.LEAF);
            setSafe(world, x, roofY - 1, BlockType.LEAF);
        }
        // 6) 石烟囱（偏一侧，穿顶而上，顶端幻菇作"烟"）
        int chimX = x0 + w - 4;
        int chimBase = top + Math.max(0, peak - Math.abs(chimX - center) / 2);
        setSafe(world, chimX, chimBase + 1, BlockType.STONE);
        setSafe(world, chimX, chimBase + 2, BlockType.STONE);
        setSafe(world, chimX, chimBase + 3, BlockType.TORCH);
        // 7) 屋内照明：火把（非实体、不挡路），钉在两侧内墙
        setSafe(world, x0 + 1, base + 4, BlockType.TORCH);
        setSafe(world, x0 + w - 2, base + 4, BlockType.TORCH);
    }

    /** 某列最靠上的实心格 y（地表）；找不到返回 -1。 */
    public static int surfaceY(LayerWorld world, int x) {
        for (int y = world.getHeight() - 3; y >= 1; y--) {
            if (world.isSolid(x, y)) {
                return y;
            }
        }
        return -1;
    }

    private static void setSafe(LayerWorld world, int x, int y, BlockType b) {
        if (world.inBounds(x, y)) {
            world.setBlock(x, y, b);
        }
    }
}
