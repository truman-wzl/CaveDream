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

    /** 初始解锁配方（工具/家具/熔炉/木箱/冶炼）。护甲/饰品/高级配方后续接入。 */
    public static List<Recipe> starter() {
        List<Recipe> r = new ArrayList<>();
        int stone = BlockType.STONE.id(), wood = BlockType.WOOD.id(), iron = BlockType.IRON_ORE.id();
        int plat = BlockType.PLATFORM.id(), leaf = BlockType.LEAF.id();
        int gold = BlockType.GOLD_ORE.id(), platOre = BlockType.PLATINUM_ORE.id(), saint = BlockType.SAINT_ORE.id();
        // 工具
        r.add(new Recipe(5001, new int[]{stone, wood}, new int[]{8, 2}, Item.pickaxe(Material.STONE), 1, true));
        r.add(new Recipe(5002, new int[]{stone, wood}, new int[]{8, 2}, Item.axe(Material.STONE), 1, true));
        r.add(new Recipe(5003, new int[]{iron, wood}, new int[]{6, 2}, Item.pickaxe(Material.IRON), 1, true));
        r.add(new Recipe(5004, new int[]{iron, wood}, new int[]{6, 2}, Item.axe(Material.IRON), 1, true));
        // 平台：1 木→4 平台
        r.add(new Recipe(5005, new int[]{wood}, new int[]{1}, Item.ofBlock(BlockType.PLATFORM), 4, true));
        // 背景墙：1 木→4 木墙
        r.add(new Recipe(5017, new int[]{wood}, new int[]{1}, Item.ofBlock(BlockType.WOOD_WALL), 4, true));
        // 家具（以平台为基元）
        r.add(new Recipe(5006, new int[]{plat}, new int[]{4}, Item.ofBlock(BlockType.TABLE), 1, true));
        r.add(new Recipe(5007, new int[]{plat}, new int[]{2}, Item.ofBlock(BlockType.CHAIR), 1, true));
        r.add(new Recipe(5008, new int[]{plat, stone}, new int[]{4, 4}, Item.ofBlock(BlockType.WORKBENCH), 1, true));
        r.add(new Recipe(5009, new int[]{plat, leaf}, new int[]{4, 2}, Item.ofBlock(BlockType.BED), 1, true));
        r.add(new Recipe(5011, new int[]{plat}, new int[]{6}, Item.ofBlock(BlockType.CHEST), 1, true));
        r.add(new Recipe(5012, new int[]{plat}, new int[]{3}, Item.ofBlock(BlockType.DOOR), 1, true));
        // 熔炉（石+平台）
        r.add(new Recipe(5010, new int[]{stone, plat}, new int[]{6, 3}, Item.ofBlock(BlockType.FURNACE), 1, true));
        // 冶炼：矿石→金属锭（需熔炉旁制作，熔炉站位校验待接）
        r.add(new Recipe(5013, new int[]{iron}, new int[]{1}, Item.IRON_INGOT, 1, true));
        r.add(new Recipe(5014, new int[]{gold}, new int[]{1}, Item.GOLD_INGOT, 1, true));
        r.add(new Recipe(5015, new int[]{platOre}, new int[]{1}, Item.PLATINUM_INGOT, 1, true));
        r.add(new Recipe(5016, new int[]{saint}, new int[]{1}, Item.SAINT_INGOT, 1, true));
        // 树苗：2 木材 → 1 树（可放置、种下后长高；也可靠砍树掉树苗）
        r.add(new Recipe(5018, new int[]{wood}, new int[]{2}, Item.ofBlock(BlockType.TREE), 1, true));
        return r;
    }
}
