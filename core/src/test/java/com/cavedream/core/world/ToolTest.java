package com.cavedream.core.world;

import com.cavedream.core.item.Material;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 工具阶梯（材质派生）挖掘力度与耗时测试：木=初始不缩短，圣=−90%。 */
class ToolTest {

    @Test
    void woodIsInitialNoReduction() {
        assertThat(Tool.pickaxe(Material.WOOD).digSeconds(BlockType.STONE))
                .isEqualTo(BlockType.STONE.digSeconds());
    }

    @Test
    void saintCuts90Percent() {
        float base = BlockType.STONE.digSeconds();
        assertThat(Tool.pickaxe(Material.SAINT).digSeconds(BlockType.STONE))
                .isCloseTo(base * 0.1f, within(0.001f));
    }

    @Test
    void tiersMonotonicallyStronger() {
        float iron = Tool.pickaxe(Material.IRON).digSeconds(BlockType.IRON_ORE);
        float gold = Tool.pickaxe(Material.GOLD).digSeconds(BlockType.IRON_ORE);
        float saint = Tool.pickaxe(Material.SAINT).digSeconds(BlockType.IRON_ORE);
        assertThat(gold).isLessThan(iron);
        assertThat(saint).isLessThan(gold);
    }

    @Test
    void powerMatchesMaterial() {
        assertThat(Tool.pickaxe(Material.STONE).power()).isEqualTo(Material.STONE.power);
        assertThat(Tool.axe(Material.WOOD).itemId()).isEqualTo(110);   // 斧 base
        assertThat(Tool.pickaxe(Material.SAINT).itemId()).isEqualTo(105);
    }

    @Test
    void unbreakableReturnsNegative() {
        assertThat(Tool.pickaxe(Material.SAINT).digSeconds(BlockType.FOG)).isEqualTo(-1f);
        assertThat(BlockType.WATER.mineable()).isFalse();
        assertThat(BlockType.DIRT.mineable()).isTrue();
    }

    @Test
    void byItemIdMapsTool() {
        assertThat(Tool.byItemId(100)).isEqualTo(Tool.pickaxe(Material.WOOD));
        assertThat(Tool.byItemId(115)).isEqualTo(Tool.axe(Material.SAINT));
        assertThat(Tool.byItemId(999)).isNull();
    }
}
