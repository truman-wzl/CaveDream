package com.cavedream.core.anim;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 挥击基础动作测试：起手→结束沿弧推进、面向镜像、静止回位。 */
class SwingTest {

    @Test
    void sweepsFromStartToEnd() {
        Swing s = new Swing(30f, -150f, 0.3f);
        assertThat(s.angle(20f, 1)).isEqualTo(20f);        // 未挥击 → 静止角
        s.restart();
        float start = s.angle(20f, 1);
        assertThat(start).isCloseTo(30f, org.assertj.core.data.Offset.offset(1f));
        for (int i = 0; i < 60; i++) {
            s.update(1 / 60f);                              // 推进 1 秒（>时长）
        }
        assertThat(s.isActive()).isFalse();
        assertThat(s.angle(20f, 1)).isEqualTo(20f);        // 结束回静止
    }

    @Test
    void midSwingSweepsDownward() {
        Swing s = Swing.chop();
        s.restart();
        s.update(0.14f);                                    // 半程
        float mid = s.angle(20f, 1);
        assertThat(mid).isLessThan(30f);                    // 已从头顶往下走
        assertThat(mid).isGreaterThan(-150f);
    }

    @Test
    void mirrorsByFacing() {
        Swing s = Swing.chop();
        s.restart();
        s.update(0.1f);
        float right = s.angle(20f, 1);
        float left = s.angle(20f, -1);
        assertThat(left).isEqualTo(-right);
    }
}
