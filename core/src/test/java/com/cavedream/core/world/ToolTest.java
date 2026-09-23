package com.cavedream.core.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 工具阶梯挖掘力度与耗时测试（GDD：木=初始不缩短，圣=−90%）。 */
class ToolTest {

    @Test
    void woodIsInitialNoReduction() {
        assertThat(Tool.WOOD_PICKAXE.digSeconds(BlockType.STONE))
                .isEqualTo(BlockType.STONE.digSeconds());   // 初始工具 = 基准耗时
    }

    @Test
    void saintCuts90Percent() {
        float base = BlockType.STONE.digSeconds();
        assertThat(Tool.SAINT_PICKAXE.digSeconds(BlockType.STONE))
                .isCloseTo(base * 0.1f, within(0.001f));    // power=10 → 耗时 10% = 减 90%
    }

    @Test
    void tiersMonotonicallyStronger() {
        float iron = Tool.IRON_PICKAXE.digSeconds(BlockType.IRON_ORE);
        float gold = Tool.GOLD_PICKAXE.digSeconds(BlockType.IRON_ORE);
        float saint = Tool.SAINT_PICKAXE.digSeconds(BlockType.IRON_ORE);
        assertThat(gold).isLessThan(iron);
        assertThat(saint).isLessThan(gold);
    }

    @Test
    void unbreakableReturnsNegative() {
        assertThat(Tool.SAINT_PICKAXE.digSeconds(BlockType.FOG)).isEqualTo(-1f);
        assertThat(BlockType.WATER.mineable()).isFalse();
        assertThat(BlockType.DIRT.mineable()).isTrue();
    }

    @Test
    void byItemIdMapsTool() {
        assertThat(Tool.byItemId(100)).isEqualTo(Tool.WOOD_PICKAXE);
        assertThat(Tool.byItemId(115)).isEqualTo(Tool.SAINT_AXE);
        assertThat(Tool.byItemId(999)).isNull();
    }
}
