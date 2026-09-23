package com.cavedream.core.light;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 静态光照引擎测试：开放天光、深度衰减、竖井导光、块光源、昼夜缩放。 */
class LightEngineTest {

    private static LayerWorld ground() {
        // 16×16：y=10..15 空气（开阔天空），y=0..9 石头（地层）
        LayerWorld w = new LayerWorld(16, 16);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y <= 9; y++) {
                w.setBlock(x, y, BlockType.STONE);
            }
        }
        return w;
    }

    @Test
    void openSkyIsBrightest() {
        LightEngine e = new LightEngine();
        e.recompute(ground());
        assertThat(e.skyAt(8, 15)).isEqualTo(LightEngine.MAX_LEVEL);   // 开阔天空满亮度
    }

    @Test
    void deepSealedInteriorIsDark() {
        LightEngine e = new LightEngine();
        e.recompute(ground());
        assertThat(e.skyAt(8, 2)).isLessThan(3);   // 深处被岩石遮蔽，天光几乎为 0
    }

    @Test
    void shaftConductsLightDown() {
        LayerWorld w = ground();
        for (int y = 1; y <= 9; y++) {
            w.setBlock(8, y, BlockType.AIR);        // 挖一条通到地表的竖井
        }
        LightEngine e = new LightEngine();
        e.recompute(w);
        assertThat(e.skyAt(8, 1)).isGreaterThan(10);        // 井底被天光照亮
        assertThat(e.skyAt(0, 1)).isLessThan(e.skyAt(8, 1)); // 远离井口处更暗
    }

    @Test
    void oreEmitsBlockLight() {
        LayerWorld w = ground();
        w.setBlock(3, 3, BlockType.DEEP_SEED);      // 自发光矿石（light=6）
        LightEngine e = new LightEngine();
        e.recompute(w);
        assertThat(e.blockAt(3, 3)).isEqualTo(6);
        assertThat(e.blockAt(3, 2)).isLessThan(6).isGreaterThan(0);   // 向邻格衰减
    }

    @Test
    void daylightScalesSkyButNotBlock() {
        LayerWorld w = ground();
        w.setBlock(3, 3, BlockType.DEEP_SEED);
        LightEngine e = new LightEngine();
        e.recompute(w);
        // 天光格：正午 vs 午夜差异巨大（选近地表、天光尚存的一格）
        assertThat(e.staticLevel(8, 9, 15)).isGreaterThan(e.staticLevel(8, 9, 0));
        // 块光格：与昼夜无关（午夜仍由矿石照亮）
        assertThat(e.staticLevel(3, 3, 0)).isEqualTo(e.staticLevel(3, 3, 15));
    }
}
