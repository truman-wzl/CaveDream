package com.cavedream.core.world.mob;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;
import com.cavedream.core.world.PlayerEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * 刷怪管理器：按昼夜系数生成/清除敌人。白天 0.5、夜间(18:30–5:59) 1.5。
 * 玩家安全圈内不刷、超远自动清除，控制上限。当前只刷史莱姆（后续扩展 Mob 类型）。纯逻辑、可单测。
 */
public final class SpawnManager {

    private static final float BASE_RATE = 0.22f;      // 系数=1 时每秒期望刷怪数
    private static final float SAFE_TILES = 9f;        // 玩家附近不刷（格）
    private static final float SPAWN_MIN_TILES = 12f;  // 最近刷点下限（会被视口半宽抬高→视野外才刷）

    private final List<Mob> mobs = new ArrayList<>();
    private final List<Mob> killed = new ArrayList<>();   // 本帧死亡（被击杀）的怪→供上层结算掉落
    private final Random rnd;
    private final int maxMobs;
    private float spawnAcc;
    private float batAcc;                 // 夜间空中蝙蝠独立计时（不受聚居安全区压制）
    private long spawnCounter;

    public SpawnManager(long seed, int maxMobs) {
        this.rnd = new Random(seed);
        this.maxMobs = maxMobs;
    }

    /** 昼夜刷怪系数：白天 0.5、夜晚 1.5。 */
    public static float coefficient(boolean isNight) {
        return isNight ? 1.5f : 0.5f;
    }

    /** 聚居安全区因子：同屏≥3 居民=小镇→0（不刷）；≥1 居民=房屋安全区→0.2；否则 1。 */
    public static float townFactor(int nearbyResidents) {
        if (nearbyResidents >= 3) {
            return 0f;
        }
        return nearbyResidents >= 1 ? 0.2f : 1f;
    }

    public List<Mob> mobs() {
        return mobs;
    }

    /** 取走并清空本帧被击杀的怪（上层据此掉铸梦币）。 */
    public List<Mob> drainKilled() {
        if (killed.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        List<Mob> out = new ArrayList<>(killed);
        killed.clear();
        return out;
    }

    public void update(LayerWorld world, float playerCenterX, float playerCenterY, boolean isNight, float dt, float viewHalfTiles, int nearbyResidents) {
        float t = PlayerEntity.TILE;
        float spawnMin = Math.max(SPAWN_MIN_TILES, viewHalfTiles + 2f);   // 至少落在视野外一点
        float spawnMax = spawnMin + 26f;
        float despawn = spawnMax + 18f;
        for (Iterator<Mob> it = mobs.iterator(); it.hasNext(); ) {
            Mob m = it.next();
            m.update(world, playerCenterX, playerCenterY, dt);
            double dist = Math.hypot(m.centerX() - playerCenterX, m.centerY() - playerCenterY) / t;
            if (!m.isAlive()) {
                killed.add(m);          // 死亡（无论近战/弹道/仆从）→集中掉币入口
                it.remove();
            } else if (dist > despawn) {
                it.remove();            // 超距清除≠击杀，不掉币
            }
        }
        spawnAcc += dt * BASE_RATE * coefficient(isNight) * townFactor(nearbyResidents);   // 聚居安全区降刷怪率
        if (spawnAcc > 4f) {
            spawnAcc = 4f;   // 累积上限，防回到场景瞬间爆刷
        }
        while (spawnAcc >= 1f && mobs.size() < maxMobs) {
            spawnAcc -= 1f;
            trySpawn(world, playerCenterX / t, playerCenterY / t, isNight, spawnMin, spawnMax);
        }
        // 夜间空中蝙蝠：独立计时、不受 townFactor 压制（高空氛围怪，村庄上空也该有）
        if (isNight) {
            batAcc += dt * 0.16f;
            while (batAcc >= 1f && mobs.size() < maxMobs) {
                batAcc -= 1f;
                trySpawnBat(world, playerCenterX / t, playerCenterY / t, spawnMin, spawnMax);
            }
        } else {
            batAcc = 0f;
        }
    }

    private void trySpawn(LayerWorld world, double playerTileX, double playerTileY, boolean isNight, float spawnMin, float spawnMax) {
        int w = world.getWidth();
        int py = (int) Math.round(playerTileY);
        for (int attempt = 0; attempt < 12; attempt++) {
            int side = rnd.nextBoolean() ? 1 : -1;
            int off = (int) (spawnMin + rnd.nextFloat() * (spawnMax - spawnMin));
            int tx = (int) Math.round(playerTileX + side * off);
            if (tx < 3 || tx > w - 4) {
                continue;
            }
            // 实心地面 + 上方两格皆 AIR（有背景墙/门则视为室内→不刷怪）
            for (int y = py + 2; y >= py - 16 && y >= 1; y--) {
                if (world.isSolid(tx, y) && world.blockAt(tx, y + 1) == BlockType.AIR && world.blockAt(tx, y + 2) == BlockType.AIR) {
                    mobs.add(Slime.onFloor(world, tx, y, rnd.nextInt(Slime.COLOR_COUNT),
                            spawnCounter++ * 2654435761L));
                    return;
                }
            }
        }
    }

    /** 夜间空中刷一只蝙蝠（玩家上方视野外找连续空气格）。 */
    private boolean trySpawnBat(LayerWorld world, double playerTileX, double playerTileY, float spawnMin, float spawnMax) {
        int w = world.getWidth();
        int t = PlayerEntity.TILE;
        for (int attempt = 0; attempt < 8; attempt++) {
            int side = rnd.nextBoolean() ? 1 : -1;
            int off = (int) (spawnMin + rnd.nextFloat() * (spawnMax - spawnMin));
            int tx = (int) Math.round(playerTileX + side * off);
            int ty = (int) (playerTileY + 2 + rnd.nextInt(10));   // 玩家上方空中（y-up→+ 为高）
            if (tx < 3 || tx > w - 4 || ty < 3) {
                continue;
            }
            if (world.blockAt(tx, ty) == BlockType.AIR && world.blockAt(tx, ty + 1) == BlockType.AIR) {
                mobs.add(new Bat(tx * (float) t, ty * (float) t, spawnCounter++ * 2654435761L));
                return true;
            }
        }
        return false;
    }
}
