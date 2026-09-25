package com.cavedream.core.world;

import com.cavedream.core.item.Material;

import java.util.HashMap;
import java.util.Map;

/**
 * 挖掘工具：由「工具类型 × 材质」派生（等价类建模，不再逐阶手写常量）。
 * 实际耗时 = 方块基准秒 / power；木=初始不缩短，圣=power10 ⇒ 减 90%。
 * 分工铁律：镐子挖除树以外的所有方块；斧子只能砍树。
 *
 * @param name   显示名
 * @param power  挖掘力度倍率（≥1）
 * @param itemId 对应物品 id（镐 100–105 / 斧 110–115）
 * @param kind   工具类型（PICKAXE/AXE）：决定与方块的可互操作范围
 */
public record Tool(String name, double power, int itemId, Kind kind) {

    /** 工具类型：决定 id 段与名称后缀。 */
    public enum Kind {
        PICKAXE("镐", 100), AXE("斧", 110);
        final String suffix;
        final int baseId;
        Kind(String suffix, int baseId) {
            this.suffix = suffix;
            this.baseId = baseId;
        }
    }

    private static final Map<Integer, Tool> BY_ID = new HashMap<>();

    static {
        for (Kind k : Kind.values()) {
            for (Material m : Material.values()) {
                Tool t = new Tool(m.cn + k.suffix, m.power, k.baseId + m.tier(), k);
                BY_ID.put(t.itemId, t);
            }
        }
    }

    /** 工厂：某类型某材质的工具。 */
    public static Tool of(Kind kind, Material material) {
        return BY_ID.get(kind.baseId + material.tier());
    }

    public static Tool pickaxe(Material m) {
        return of(Kind.PICKAXE, m);
    }

    public static Tool axe(Material m) {
        return of(Kind.AXE, m);
    }

    /** 初始工具：木镐。 */
    public static final Tool INITIAL = pickaxe(Material.WOOD);

    /** 由物品 id 反查工具；非工具返回 null。 */
    public static Tool byItemId(int itemId) {
        return BY_ID.get(itemId);
    }

    public Tool {
        if (power < 1.0) {
            power = 1.0;
        }
    }

    /** 用本工具挖掘 b 的实际耗时（秒）；b 不可挖、或工具与方块不匹配（斧只能砍树、镐不能砍树）→ -1。 */
    public float digSeconds(BlockType b) {
        if (b == null || !b.mineable()) {
            return -1f;
        }
        // 工具分工：斧只砍树（非树→-1）；镐挖一切但不砍树（树→-1）。
        if (kind == Kind.AXE) {
            if (!b.isTree()) {
                return -1f;
            }
        } else {
            if (b.isTree()) {
                return -1f;
            }
        }
        float base = b.digSeconds();
        if (base <= 0f) {
            return 0f;
        }
        return (float) Math.max(0.03, base / power);
    }
}
