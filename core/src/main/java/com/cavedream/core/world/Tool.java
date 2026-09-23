package com.cavedream.core.world;

import com.cavedream.core.item.Item;

/**
 * 挖掘工具阶梯（GDD）：木→石→铁→金→白金→圣，power=挖掘力度倍率。
 * 实际耗时 = 方块基准秒 / power；木（初始）power=1.0 不缩短；圣 power=10 ⇒ 耗时=基准/10（减 90%）。
 * 每个工具绑定其 {@link Item} id，便于从背包选中格反查工具。
 *
 * @param name   工具名（HUD/背包）
 * @param power  挖掘力度倍率（≥1）
 * @param itemId 对应物品 id（镐 100–105 / 斧 110–115）
 */
public record Tool(String name, double power, int itemId) {

    public static final Tool WOOD_PICKAXE = new Tool("木镐", 1.0, 100);
    public static final Tool STONE_PICKAXE = new Tool("石镐", 1.5, 101);
    public static final Tool IRON_PICKAXE = new Tool("铁镐", 2.0, 102);
    public static final Tool GOLD_PICKAXE = new Tool("金镐", 3.0, 103);
    public static final Tool PLATINUM_PICKAXE = new Tool("白金镐", 4.5, 104);
    public static final Tool SAINT_PICKAXE = new Tool("圣镐", 10.0, 105);   // −90% 挖掘时间

    public static final Tool WOOD_AXE = new Tool("木斧", 1.0, 110);
    public static final Tool STONE_AXE = new Tool("石斧", 1.5, 111);
    public static final Tool IRON_AXE = new Tool("铁斧", 2.0, 112);
    public static final Tool GOLD_AXE = new Tool("金斧", 3.0, 113);
    public static final Tool PLATINUM_AXE = new Tool("白金斧", 4.5, 114);
    public static final Tool SAINT_AXE = new Tool("圣斧", 10.0, 115);

    /** 初始工具：木镐，不缩短任何挖掘时间（GDD：初始工具无法缩短挖掘时间）。 */
    public static final Tool INITIAL = WOOD_PICKAXE;

    public Tool {
        if (power < 1.0) {
            power = 1.0;
        }
    }

    /** 由背包物品 id 反查工具（镐优先）；非工具返回 null。 */
    public static Tool byItemId(int itemId) {
        for (Tool t : new Tool[]{WOOD_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, GOLD_PICKAXE,
                PLATINUM_PICKAXE, SAINT_PICKAXE, WOOD_AXE, STONE_AXE, IRON_AXE, GOLD_AXE,
                PLATINUM_AXE, SAINT_AXE}) {
            if (t.itemId == itemId) {
                return t;
            }
        }
        return null;
    }

    /** 用本工具挖掘 b 的实际耗时（秒）；b 不可挖返回 -1。 */
    public float digSeconds(BlockType b) {
        if (b == null || !b.mineable()) {
            return -1f;
        }
        float base = b.digSeconds();
        if (base <= 0f) {
            return 0f;
        }
        return (float) Math.max(0.03, base / power);
    }
}
