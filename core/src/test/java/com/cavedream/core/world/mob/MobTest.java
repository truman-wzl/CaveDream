package com.cavedream.core.world.mob;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 史莱姆物理与昼夜刷怪测试。 */
class MobTest {

    private static final int T = 16;

    private static LayerWorld flat(int w, int h, int groundTop) {
        LayerWorld world = new LayerWorld(w, h);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y <= groundTop; y++) {
                world.setBlock(x, y, BlockType.STONE);
            }
        }
        return world;
    }

    @Test
    void slimeRestsOnGround() {
        LayerWorld world = flat(40, 30, 5);
        Slime s = Slime.onSurface(world, 20, 0, 1L);
        for (int i = 0; i < 120; i++) {
            s.update(world, 20 * T, 20 * T, 1 / 60f);
        }
        assertThat(s.isOnGround()).isTrue();
        assertThat(s.y()).isGreaterThanOrEqualTo(6 * T - 1f);   // 站在地表(顶=5)之上
    }

    @Test
    void slimeHopsTowardPlayer() {
        LayerWorld world = flat(60, 30, 5);
        Slime s = Slime.onSurface(world, 20, 0, 7L);
        float startX = s.centerX();
        float playerX = 45 * T;                                 // 玩家在右侧
        for (int i = 0; i < 60 * 8; i++) {                      // 8 秒
            s.update(world, playerX, 6 * T, 1 / 60f);
        }
        assertThat(s.centerX()).isGreaterThan(startX);          // 朝玩家(右)移动了
    }

    @Test
    void damageKillsAtZero() {
        LayerWorld world = new LayerWorld(20, 20);
        Slime s = new Slime(100, 100, 3, 1L);
        assertThat(s.damage(10)).isFalse();
        assertThat(s.hp()).isEqualTo(14);
        assertThat(s.damage(999)).isFalse();   // 无敌帧内不叠加（防灌伤）
        for (int i = 0; i < 40; i++) {
            s.update(world, 0, 0, 1 / 60f);      // 推进 0.66s 越过无敌帧
        }
        assertThat(s.damage(999)).isTrue();
        assertThat(s.isAlive()).isFalse();
    }

    @Test
    void coefficientDayNight() {
        assertThat(SpawnManager.coefficient(false)).isEqualTo(0.5f);
        assertThat(SpawnManager.coefficient(true)).isEqualTo(1.5f);
    }

    @Test
    void nightSpawnsMoreThanDay() {
        LayerWorld world = flat(200, 40, 8);
        SpawnManager day = new SpawnManager(42, 40);
        SpawnManager night = new SpawnManager(42, 40);
        int steps = 60 * 60;                                    // 60 秒
        for (int i = 0; i < steps; i++) {
            day.update(world, 100 * T, 10 * T, false, 1 / 60f, 10f, 0);
            night.update(world, 100 * T, 10 * T, true, 1 / 60f, 10f, 0);
        }
        assertThat(night.mobs().size()).isGreaterThan(day.mobs().size());
    }
}
