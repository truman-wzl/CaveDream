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
    void outOfBoundsIsSafe() {
        LightEngine e = new LightEngine();
        e.recompute(ground());
        assertThat(e.skyAt(-1, 0)).isZero();
        assertThat(e.skyAt(0, 9999)).isZero();
        assertThat(e.blockAt(9999, 9999)).isZero();
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

    /** 无背景墙→可透光；同格有背景墙→完全不透（天光进入即被拦，恒为 0）。 */
    @Test
    void wallStopsSkyTransmission() {
        LayerWorld unwalled = ground();
        unwalled.setBlock(8, 5, BlockType.AIR);        // 地下挖一个空格、无背景墙
        LightEngine e = new LightEngine();
        e.recompute(unwalled);
        int openLight = e.skyAt(8, 5);

        LayerWorld walled = ground();
        walled.setBlock(8, 5, BlockType.AIR);
        walled.setBackWall(8, 5, true);                // 同位铺背景墙
        LightEngine e2 = new LightEngine();
        e2.recompute(walled);
        assertThat(e2.skyAt(8, 5)).as("有背景墙→不透光→黑").isZero();
        assertThat(openLight).as("无墙同格有渗露天光").isGreaterThan(e2.skyAt(8, 5));
    }

    /** 浮岛（固体、无墙）不遮黑其下方开阔空：天光经侧向连通漫灌到满天光。 */
    @Test
    void underFloatingIslandStaysLitViaConnectivity() {
        LayerWorld w = new LayerWorld(20, 16);         // 全空气
        for (int x = 5; x < 15; x++) {
            w.setBlock(x, 12, BlockType.STONE);        // 悬石条，左右各留开口（不横贯）
        }
        LightEngine e = new LightEngine();
        e.recompute(w);
        assertThat(e.skyAt(9, 11)).as("石条下方空气经侧向连通→满天光").isEqualTo(LightEngine.MAX_LEVEL);
        assertThat(e.skyAt(9, 13)).as("石条上方开阔天空光").isEqualTo(LightEngine.MAX_LEVEL);
    }
}
