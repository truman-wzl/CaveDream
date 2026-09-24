package com.cavedream.core.player;

import com.cavedream.core.item.Item;

/**
 * 装备/能力槽（GDD §3.5）：职业两格（天赋=被动、技能=主动）+ 护甲 4（头盔/战甲/护腿/战靴）+ 饰品 3。
 * 装备页按"分组标题 + 格子竖向排列"呈现。纯逻辑、可单测。
 * 当前物品体系仅有 BLOCK/TOOL/WEAPON，尚无护甲/饰品物品，故槽位均为空占位；
 * 待 ARMOR/ACCESSORY 物品接入后 {@link #slotFor} 再返回对应槽。
 */
public final class Equipment {

    public static final int PASSIVE = 0;         // 职业被动（天赋）
    public static final int SKILL = 1;           // 职业技能
    public static final int ARMOR_FIRST = 2;     // 2..5 头盔/战甲/护腿/战靴
    public static final int ACC_FIRST = 6;       // 6..8 饰品
    public static final int SLOTS = 9;

    private final Item[] slots = new Item[SLOTS];

    public Item get(int i) {
        return i >= 0 && i < SLOTS ? slots[i] : null;
    }

    /** 该物品应放入的槽位；不可装备返回 -1（护甲/饰品物品体系接入后再实现）。 */
    public static int slotFor(Item it) {
        return -1;
    }

    /** 装备物品，返回被替换下来的旧物品（可空）。不可装备返回 null 且不变。 */
    public Item equip(Item it) {
        int s = slotFor(it);
        if (s < 0) {
            return null;
        }
        Item old = slots[s];
        slots[s] = it;
        return old;
    }

    /** 卸下某槽，返回该物品（可空）。 */
    public Item unequip(int slot) {
        if (slot < 0 || slot >= SLOTS) {
            return null;
        }
        Item old = slots[slot];
        slots[slot] = null;
        return old;
    }
}
