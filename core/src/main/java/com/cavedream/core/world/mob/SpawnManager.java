package com.cavedream.core.world.mob;

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
    private static final float SPAWN_MIN_TILES = 12f;  // 最近刷点距离
    private static final float SPAWN_MAX_TILES = 40f;  // 最远刷点距离
    private static final float DESPAWN_TILES = 56f;    // 超此距离清除

    private final List<Mob> mobs = new ArrayList<>();
    private final Random rnd;
    private final int maxMobs;
    private float spawnAcc;
    private long spawnCounter;

    public SpawnManager(long seed, int maxMobs) {
        this.rnd = new Random(seed);
        this.maxMobs = maxMobs;
    }

    /** 昼夜刷怪系数：白天 0.5、夜晚 1.5。 */
    public static float coefficient(boolean isNight) {
        return isNight ? 1.5f : 0.5f;
    }

    public List<Mob> mobs() {
        return mobs;
    }

    public void update(LayerWorld world, float playerCenterX, float playerCenterY, boolean isNight, float dt) {
        float t = PlayerEntity.TILE;
        for (Iterator<Mob> it = mobs.iterator(); it.hasNext(); ) {
            Mob m = it.next();
            m.update(world, playerCenterX, playerCenterY, dt);
            double dist = Math.hypot(m.centerX() - playerCenterX, m.centerY() - playerCenterY) / t;
            if (!m.isAlive() || dist > DESPAWN_TILES) {
                it.remove();
            }
        }
        spawnAcc += dt * BASE_RATE * coefficient(isNight);
        if (spawnAcc > 4f) {
            spawnAcc = 4f;   // 累积上限，防回到场景瞬间爆刷
        }
        while (spawnAcc >= 1f && mobs.size() < maxMobs) {
            spawnAcc -= 1f;
            trySpawn(world, playerCenterX / t, playerCenterY / t);
        }
    }

    private void trySpawn(LayerWorld world, double playerTileX, double playerTileY) {
        int w = world.getWidth();
        int py = (int) Math.round(playerTileY);
        for (int attempt = 0; attempt < 12; attempt++) {
            int side = rnd.nextBoolean() ? 1 : -1;
            int off = (int) (SPAWN_MIN_TILES + rnd.nextFloat() * (SPAWN_MAX_TILES - SPAWN_MIN_TILES));
            int tx = (int) Math.round(playerTileX + side * off);
            if (tx < 3 || tx > w - 4) {
                continue;
            }
            // 在玩家同一高度带内找可站立的实心面（顶两格为空）→避免在浮岛旁的空天柱一路找到地底
            for (int y = py + 2; y >= py - 16 && y >= 1; y--) {
                if (world.isSolid(tx, y) && !world.isSolid(tx, y + 1) && !world.isSolid(tx, y + 2)) {
                    mobs.add(Slime.onFloor(world, tx, y, rnd.nextInt(Slime.COLOR_COUNT),
                            spawnCounter++ * 2654435761L));
                    return;
                }
            }
        }
    }
}
