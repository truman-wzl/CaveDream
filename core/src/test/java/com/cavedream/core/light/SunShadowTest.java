package com.cavedream.core.light;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 方向光阴影测试：正午顶光被遮板挡住→下方全阴、上方全亮；低太阳不投影。 */
class SunShadowTest {

    private static LayerWorld withSlab() {
        LayerWorld w = new LayerWorld(20, 20);
        for (int x = 0; x < 20; x++) {
            w.setBlock(x, 10, BlockType.STONE);   // 一道水平遮板
        }
        return w;
    }

    @Test
    void overheadSunCastsShadowBelowSlab() {
        LayerWorld w = withSlab();
        assertThat(SunShadow.visibility(w, 5, 5, 0f, 1f, 16)).isZero();     // 板下：全阴影
        assertThat(SunShadow.visibility(w, 5, 15, 0f, 1f, 16)).isEqualTo(1f); // 板上：直射
    }

    @Test
    void lowSunDoesNotCast() {
        LayerWorld w = withSlab();
        // dirY≈0（晨昏掠射）→ 不投影，返回全可见
        assertThat(SunShadow.visibility(w, 5, 5, 1f, 0f, 16)).isEqualTo(1f);
    }

    @Test
    void openCellFullyLit() {
        LayerWorld w = new LayerWorld(20, 20);   // 全空
        assertThat(SunShadow.visibility(w, 10, 10, 0.5f, 0.87f, 16)).isEqualTo(1f);
    }
}
