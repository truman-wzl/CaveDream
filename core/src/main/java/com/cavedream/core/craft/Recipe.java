package com.cavedream.core.craft;

import com.cavedream.core.inventory.Inventory;
import com.cavedream.core.item.Item;
import com.cavedream.core.item.Material;
import com.cavedream.core.world.BlockType;

import java.util.ArrayList;
import java.util.List;

/**
 * 合成配方（“一切皆 ID”：配方也有 id，段 5000+）。输入=若干物品 id+数量，输出=物品+数量。
 * 纯逻辑、可单测。可合成判定基于背包持有量。
 */
public final class Recipe {

    public final int id;
    public final int[] inIds;
    public final int[] inCounts;
    public final Item out;
    public final int outCount;
    public final boolean unlocked;   // 未解锁的配方不在工作区显示

    public Recipe(int id, int[] inIds, int[] inCounts, Item out, int outCount, boolean unlocked) {
        this.id = id;
        this.inIds = inIds;
        this.inCounts = inCounts;
        this.out = out;
        this.outCount = outCount;
        this.unlocked = unlocked;
    }

    /** 背包料是否齐全。 */
    public boolean canCraft(Inventory inv) {
        for (int i = 0; i < inIds.length; i++) {
            Item it = Item.byId(inIds[i]);
            if (it == null || inv.countOf(it) < inCounts[i]) {
                return false;
            }
        }
        return true;
    }

    /** 尝试合成：料齐则扣料并产出。返回是否成功。 */
    public boolean craft(Inventory inv) {
        if (!canCraft(inv)) {
            return false;
        }
        for (int i = 0; i < inIds.length; i++) {
            inv.remove(Item.byId(inIds[i]), inCounts[i]);
        }
        inv.add(out, outCount);
        return true;
    }

    /** 初始解锁配方（用现有方块/工具；护甲/饰品/高级配方后续接入）。 */
    public static List<Recipe> starter() {
        List<Recipe> r = new ArrayList<>();
        int stone = BlockType.STONE.id(), wood = BlockType.WOOD.id(), iron = BlockType.IRON_ORE.id();
        r.add(new Recipe(5001, new int[]{stone, wood}, new int[]{8, 2}, Item.pickaxe(Material.STONE), 1, true));
        r.add(new Recipe(5002, new int[]{stone, wood}, new int[]{8, 2}, Item.axe(Material.STONE), 1, true));
        r.add(new Recipe(5003, new int[]{iron, wood}, new int[]{6, 2}, Item.pickaxe(Material.IRON), 1, true));
        r.add(new Recipe(5004, new int[]{iron, wood}, new int[]{6, 2}, Item.axe(Material.IRON), 1, true));
        return r;
    }
}
