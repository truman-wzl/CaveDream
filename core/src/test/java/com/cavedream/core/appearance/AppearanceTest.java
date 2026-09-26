package com.cavedream.core.appearance;

import com.cavedream.core.anim.rig.DreamerRig;
import com.cavedream.core.anim.rig.SkinBinding;
import com.cavedream.core.render.PaintedLook;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 外观锻造（纯逻辑）：确定性、部位合法、蓝图往返、骰子可绑定、锁定重掷。 */
class AppearanceTest {

    @Test
    void forgeIsDeterministic() {
        LookSpec s = LookDice.roll(0x1234ABCDL);
        PaintedLook a = PixelLookForge.forge(s);
        PaintedLook b = PixelLookForge.forge(s);
        assertThat(a.colors).containsExactly(b.colors);
        assertThat(a.regions()).containsExactly(b.regions());
    }

    @Test
    void forgedLookHasClassifiedBody() {
        PaintedLook l = PixelLookForge.defaultLook();
        assertThat(l.colors.length).isEqualTo(Humanoid.W * Humanoid.H);
        assertThat(l.regions().length).isEqualTo(l.colors.length);
        int classified = 0;
        for (byte r : l.regions()) {
            assertThat(r).as("region 只应 0..8").isBetween((byte) 0, (byte) 8);
            if (r >= 1 && r <= 8) {
                classified++;
            }
        }
        assertThat(classified).as("应有大量像素归到部位").isGreaterThan(200);
        assertThat(l.bounds()).isNotNull();
    }

    @Test
    void specRoundTripsThroughParams() {
        LookSpec s = LookDice.roll(0xFEEDFACE01L);
        LookSpec r = LookSpec.fromParams(s.toParams());
        assertThat(r.toParams()).containsExactly(s.toParams());
    }

    @Test
    void diceIsReproducibleAndEveryRollBinds() {
        for (long seed : new long[]{1L, 42L, 999L, 0x5A5AL, Long.MIN_VALUE}) {
            assertThat(LookDice.roll(seed).toParams()).containsExactly(LookDice.roll(seed).toParams());
            SkinBinding sb = DreamerRig.bind(PixelLookForge.forge(LookDice.roll(seed)));
            assertThat(sb.boundCount()).as("seed=" + seed + " 应可绑定大量像素").isGreaterThan(150);
        }
    }

    @Test
    void rerollKeepsLockedBody() {
        LookSpec base = LookDice.roll(7L);
        // 锁定体型后重掷，体型字段应保持不变
        LookSpec out = LookDice.reroll(base, 0x9E3779B9L, LookDice.BODY);
        assertThat(out.headScale).isEqualTo(base.headScale);
        assertThat(out.torsoScale).isEqualTo(base.torsoScale);
        assertThat(out.legScale).isEqualTo(base.legScale);
        assertThat(out.shoulderScale).isEqualTo(base.shoulderScale);
        // 未锁的发型/配饰应采纳新掷（若与 base 相同则说明骰子退化，用大差异 seed 规避）
        assertThat(Arrays.equals(out.toParams(), base.toParams())).isFalse();
    }
}
