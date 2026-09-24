package com.cavedream.core.craft;

import com.cavedream.core.inventory.Inventory;
import com.cavedream.core.item.Item;
import com.cavedream.core.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 合成配方测试：料齐才可合成，craft 扣料产出；缺料判定正确。 */
class RecipeTest {

    private Recipe first() {
        List<Recipe> rs = Recipe.starter();
        return rs.get(0);                            // 石镐：石头×8 + 木×2
    }

    @Test
    void cannotCraftWithoutMaterials() {
        Inventory inv = new Inventory(40);
        assertThat(first().canCraft(inv)).isFalse();
    }

    @Test
    void craftConsumesAndProduces() {
        Recipe r = first();
        Inventory inv = new Inventory(40);
        inv.add(Item.ofBlock(BlockType.STONE), 8);
        inv.add(Item.ofBlock(BlockType.WOOD), 2);
        assertThat(r.canCraft(inv)).isTrue();
        assertThat(r.craft(inv)).isTrue();
        assertThat(inv.countOf(Item.ofBlock(BlockType.STONE))).isZero();
        assertThat(inv.countOf(Item.ofBlock(BlockType.WOOD))).isZero();
        assertThat(inv.countOf(r.out)).isEqualTo(1);
    }
}
