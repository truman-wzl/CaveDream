package com.cavedream.core.inventory;

import com.cavedream.core.item.Item;

/**
 * 背包/快捷栏（承载任意 {@link Item}：方块可堆叠，工具/武器不堆叠 cap=1）。
 * 纯逻辑、可脱离窗口单测。槽位以物品 id 标识，-1 为空。
 */
public final class Inventory {

    public static final int BLOCK_STACK_CAP = 999;
    public static final int EMPTY = -1;

    private final int[] itemIds;
    private final int[] counts;
    private final int size;
    private int selected;

    public Inventory(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("槽位数必须为正：" + size);
        }
        this.size = size;
        this.itemIds = new int[size];
        this.counts = new int[size];
        java.util.Arrays.fill(itemIds, EMPTY);
    }

    public int size() {
        return size;
    }

    public int selected() {
        return selected;
    }

    public void select(int i) {
        if (i >= 0 && i < size) {
            selected = i;
        }
    }

    public void scroll(int dir) {
        selected = Math.floorMod(selected + dir, size);
    }

    public Item itemAt(int i) {
        return itemIds[i] == EMPTY ? null : Item.byId(itemIds[i]);
    }

    public int countAt(int i) {
        return counts[i];
    }

    /** 当前选中格物品（空则 null）。 */
    public Item selectedItem() {
        return itemAt(selected);
    }

    private static int capOf(Item it) {
        return it.kind() == Item.Kind.BLOCK ? BLOCK_STACK_CAP : 1;
    }

    /** 放入 count 个物品：先叠加同类未满堆，再占空格；返回是否全部放入。 */
    public boolean add(Item it, int count) {
        if (it == null || count <= 0) {
            return false;
        }
        int remaining = count;
        int cap = capOf(it);
        for (int i = 0; i < size && remaining > 0; i++) {
            if (itemIds[i] == it.id() && counts[i] < cap) {
                int put = Math.min(cap - counts[i], remaining);
                counts[i] += put;
                remaining -= put;
            }
        }
        for (int i = 0; i < size && remaining > 0; i++) {
            if (itemIds[i] == EMPTY) {
                int put = Math.min(cap, remaining);
                itemIds[i] = it.id();
                counts[i] = put;
                remaining -= put;
            }
        }
        return remaining == 0;
    }

    /** 从选中格取走 1 个（放置/消耗）；返回该物品，格空则 null。 */
    public Item takeSelectedOne() {
        if (itemIds[selected] == EMPTY || counts[selected] <= 0) {
            return null;
        }
        Item it = Item.byId(itemIds[selected]);
        counts[selected]--;
        if (counts[selected] <= 0) {
            counts[selected] = 0;
            itemIds[selected] = EMPTY;
        }
        return it;
    }

    /** 某物品总数量。 */
    public int countOf(Item it) {
        int n = 0;
        for (int i = 0; i < size; i++) {
            if (itemIds[i] == it.id()) {
                n += counts[i];
            }
        }
        return n;
    }

    /** 移除最多 count 个该物品（合成扣料）；返回实际移除数。 */
    public int remove(Item it, int count) {
        if (it == null || count <= 0) {
            return 0;
        }
        int remaining = count;
        for (int i = 0; i < size && remaining > 0; i++) {
            if (itemIds[i] == it.id()) {
                int take = Math.min(counts[i], remaining);
                counts[i] -= take;
                remaining -= take;
                if (counts[i] <= 0) {
                    counts[i] = 0;
                    itemIds[i] = EMPTY;
                }
            }
        }
        return count - remaining;
    }

    /** 快照：各槽物品 id（供存档）。 */
    public int[] itemIdSnapshot() {
        return itemIds.clone();
    }

    /** 快照：各槽数量（供存档）。 */
    public int[] countSnapshot() {
        return counts.clone();
    }

    /** 从存档恢复槽位（越界部分忽略）。 */
    public void loadFrom(int[] ids, int[] cnts, int sel) {
        java.util.Arrays.fill(itemIds, EMPTY);
        java.util.Arrays.fill(counts, 0);
        if (ids != null && cnts != null) {
            for (int i = 0; i < size && i < ids.length && i < cnts.length; i++) {
                itemIds[i] = ids[i];
                counts[i] = ids[i] == EMPTY ? 0 : cnts[i];
            }
        }
        select(sel);
    }
}
