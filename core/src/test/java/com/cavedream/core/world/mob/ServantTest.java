package com.cavedream.core.world.mob;

import com.cavedream.core.world.LayerWorld;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 召唤仆从 AI 测试：自动锁定最近的怪、飞近并持续造成伤害；永不因时间消失。 */
class ServantTest {

    @Test
    void seeksAndDamagesNearestMob() {
        LayerWorld world = new LayerWorld(40, 30);
        List<Mob> mobs = new ArrayList<>();
        Slime slime = new Slime(160, 100, 0, 1L);
        mobs.add(slime);
        Servant sv = new Servant(120, 100, 30, 10, 0f);   // orbitPhase=0
        float pcx = 100f, pcy = 100f;
        int before = slime.hp();
        boolean alive = true;
        for (int i = 0; i < 60 * 3; i++) {                 // 3 秒
            alive = sv.update(pcx, pcy, mobs, 1 / 60f);
            slime.update(world, pcx, pcy, 1 / 60f);
        }
        assertThat(slime.hp()).isLessThan(before);         // 仆从打到了怪
        assertThat(alive).isTrue();                         // 仆从永久存在（不因时间消失）
    }

    @Test
    void persistsIndefinitely() {
        Servant sv = new Servant(100, 100, 30, 10, 1f);
        List<Mob> empty = new ArrayList<>();
        boolean alive = true;
        for (int i = 0; i < 60 * 120; i++) {                // 模拟 2 分钟
            alive = sv.update(100, 100, empty, 1 / 60f);
        }
        assertThat(alive).isTrue();                          // 无怪也不消失
    }
}
