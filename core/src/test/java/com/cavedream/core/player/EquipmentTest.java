package com.cavedream.core.player;

import com.cavedream.core.item.Item;
import com.cavedream.core.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 装备槽测试：武器可装、方块不可装；装备/卸下正确交换。 */
class EquipmentTest {

    @Test
    void onlyWeaponEquips() {
        Item sword = Item.byId(200);                 // 职业武器（WEAPON）
        assertThat(sword.kind()).isEqualTo(Item.Kind.WEAPON);
        assertThat(Equipment.slotFor(sword)).isEqualTo(Equipment.WEAPON);
        assertThat(Equipment.slotFor(Item.ofBlock(BlockType.STONE))).isEqualTo(-1);
    }

    @Test
    void equipSwapsOutOld() {
        Equipment eq = new Equipment();
        Item a = Item.byId(200);
        Item b = Item.byId(201);
        assertThat(eq.equip(a)).isNull();
        assertThat(eq.get(Equipment.WEAPON)).isEqualTo(a);
        assertThat(eq.equip(b)).isEqualTo(a);        // 返回被替换的旧武器
        assertThat(eq.get(Equipment.WEAPON)).isEqualTo(b);
        assertThat(eq.unequip(Equipment.WEAPON)).isEqualTo(b);
        assertThat(eq.get(Equipment.WEAPON)).isNull();
    }
}
