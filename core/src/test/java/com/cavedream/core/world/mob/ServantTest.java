package com.cavedream.core.world.mob;

import com.cavedream.core.world.LayerWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 召唤仆从 AI 测试：自动锁定最近的怪、飞近并持续造成伤害。 */
class ServantTest {

    @Test
    void seeksAndDamagesNearestMob() {
        LayerWorld world = new LayerWorld(40, 30);
        List<Mob> mobs = new ArrayList<>();
        Slime slime = new Slime(160, 100, 0, 1L);
        mobs.add(slime);
        Servant sv = new Servant(120, 100, 30, 10, 20f);   // 玩家附近(100,100)
        float pcx = 100f, pcy = 100f;
        int before = slime.hp();
        for (int i = 0; i < 60 * 3; i++) {                 // 3 秒
            sv.update(pcx, pcy, mobs, 1 / 60f);
            slime.update(world, pcx, pcy, 1 / 60f);        // 让怪也动一下
        }
        assertThat(slime.hp()).isLessThan(before);         // 仆从打到了怪
        assertThat(sv.life()).isGreaterThan(0f);           // 仆从仍在场
    }

    @Test
    void expiresAfterLife() {
        Servant sv = new Servant(100, 100, 30, 10, 1f);
        List<Mob> empty = new ArrayList<>();
        boolean alive = true;
        for (int i = 0; i < 60 * 2; i++) {
            alive = sv.update(100, 100, empty, 1 / 60f);
        }
        assertThat(alive).isFalse();                        // 寿命到 → 消失
    }
}
