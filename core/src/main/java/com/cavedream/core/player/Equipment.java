package com.cavedream.core.player;

import com.cavedream.core.item.Item;

/**
 * 装备槽（GDD §3.5）：武器 1 + 护甲 4（头盔/战甲/护腿/战靴）+ 饰品 3。
 * 纯逻辑、可单测。当前物品体系只有"武器"可装备（护甲/饰品物品后续接入），槽位先占位。
 */
public final class Equipment {

    public static final int WEAPON = 0;
    public static final int ARMOR_FIRST = 1;   // 1..4
    public static final int ACC_FIRST = 5;     // 5..7
    public static final int SLOTS = 8;

    private final Item[] slots = new Item[SLOTS];

    public Item get(int i) {
        return i >= 0 && i < SLOTS ? slots[i] : null;
    }

    /** 该物品应放入的槽位；不可装备返回 -1。 */
    public static int slotFor(Item it) {
        if (it == null) {
            return -1;
        }
        if (it.kind() == Item.Kind.WEAPON) {
            return WEAPON;   // 目前仅武器可装备；护甲/饰品待物品接入
        }
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
