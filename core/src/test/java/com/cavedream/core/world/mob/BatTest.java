package com.cavedream.core.world.mob;

import com.cavedream.core.world.LayerWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 蝙蝠测试：不受重力、朝玩家水平逼近、掉币 3~6、typeId=1001。 */
class BatTest {

    @Test
    void fliesTowardPlayerWithoutGravity() {
        LayerWorld world = new LayerWorld(60, 40);
        Bat b = new Bat(100f, 100f, 1L);
        for (int i = 0; i < 10; i++) {
            b.update(world, 300f, 100f, 1 / 60f);   // 玩家在右侧远处
        }
        assertThat(b.vx).isGreaterThan(0f);          // 朝玩家（右）飞
        assertThat(b.isOnGround()).isFalse();        // 飞行不落地
    }

    @Test
    void rollsThreeToSixCoinsAndHasTypeId() {
        Bat b = new Bat(0f, 0f, 1L);
        assertThat(b.typeId()).isEqualTo(1001);
        for (int i = 0; i < 100; i++) {
            assertThat(b.rollCoins()).isBetween(3, 6);
        }
    }
}
