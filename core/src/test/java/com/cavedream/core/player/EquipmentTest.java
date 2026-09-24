package com.cavedream.core.player;

import com.cavedream.core.item.Item;
import com.cavedream.core.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 装备槽测试：当前无 ARMOR/饰品物品→武器/方块均不可装；越界卸下安全。 */
class EquipmentTest {

    @Test
    void nothingEquippableYet() {
        assertThat(Equipment.slotFor(Item.byId(200))).isEqualTo(-1);      // 武器不再进装备栏
        assertThat(Equipment.slotFor(Item.ofBlock(BlockType.STONE))).isEqualTo(-1);
        Equipment eq = new Equipment();
        assertThat(eq.equip(Item.byId(200))).isNull();                   // 不可装→null 且不变
        assertThat(eq.get(Equipment.PASSIVE)).isNull();
    }

    @Test
    void nineSlotsAndSafeUnequip() {
        Equipment eq = new Equipment();
        assertThat(Equipment.SLOTS).isEqualTo(9);
        assertThat(eq.unequip(Equipment.ARMOR_FIRST)).isNull();           // 空槽卸下返回 null
        assertThat(eq.unequip(-1)).isNull();
        assertThat(eq.unequip(99)).isNull();
    }
}
