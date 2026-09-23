package com.cavedream.core.inventory;

import com.cavedream.core.item.Item;
import com.cavedream.core.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 背包堆叠/消耗/选中切换测试（方块可堆叠、工具不堆叠）。 */
class InventoryTest {

    @Test
    void addStacksSameBlockFirst() {
        Inventory inv = new Inventory(10);
        assertThat(inv.add(Item.ofBlock(BlockType.DIRT), 5)).isTrue();
        assertThat(inv.countOf(Item.ofBlock(BlockType.DIRT))).isEqualTo(5);
        assertThat(inv.itemAt(0).block()).isEqualTo(BlockType.DIRT);
        inv.add(Item.ofBlock(BlockType.DIRT), 3);
        assertThat(inv.countAt(0)).isEqualTo(8);   // 叠加到同格
    }

    @Test
    void toolsDoNotStack() {
        Inventory inv = new Inventory(10);
        inv.add(Item.WOOD_PICKAXE, 1);
        inv.add(Item.WOOD_PICKAXE, 1);   // cap=1 → 溢出到下一格
        assertThat(inv.countAt(0)).isEqualTo(1);
        assertThat(inv.countAt(1)).isEqualTo(1);
    }

    @Test
    void overflowSpillsToNextSlot() {
        Inventory inv = new Inventory(10);
        inv.add(Item.ofBlock(BlockType.STONE), Inventory.BLOCK_STACK_CAP + 1);
        assertThat(inv.countAt(0)).isEqualTo(Inventory.BLOCK_STACK_CAP);
        assertThat(inv.countAt(1)).isEqualTo(1);
    }

    @Test
    void takeSelectedOneConsumesThenEmpties() {
        Inventory inv = new Inventory(10);
        inv.add(Item.ofBlock(BlockType.WOOD), 2);
        inv.select(0);
        assertThat(inv.takeSelectedOne().block()).isEqualTo(BlockType.WOOD);
        assertThat(inv.takeSelectedOne().block()).isEqualTo(BlockType.WOOD);
        assertThat(inv.takeSelectedOne()).isNull();
        assertThat(inv.itemAt(0)).isNull();
    }

    @Test
    void scrollCyclesSelection() {
        Inventory inv = new Inventory(3);
        inv.scroll(1);
        assertThat(inv.selected()).isEqualTo(1);
        inv.scroll(-2);
        assertThat(inv.selected()).isEqualTo(2);
        inv.scroll(1);
        assertThat(inv.selected()).isZero();
    }

    @Test
    void starterKitAreToolAndWeaponItems() {
        Inventory inv = new Inventory(10);
        for (Item it : Item.STARTER_KIT) {
            inv.add(it, 1);
        }
        assertThat(inv.itemAt(0)).isEqualTo(Item.WOOD_PICKAXE);
        assertThat(inv.itemAt(1)).isEqualTo(Item.WOOD_AXE);
        assertThat(inv.itemAt(2)).isEqualTo(Item.WARRIOR_SWORD);
    }
}
