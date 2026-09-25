package com.cavedream.core.item;

import com.cavedream.core.world.BlockType;

import java.util.HashMap;
import java.util.Map;

/**
 * 统一物品注册表（GDD 物品/背包系统）。用稳定的整数 id 贯穿客户端与 t_item_def：
 * 方块 0–12（沿用 {@link BlockType} id，禁改）；工具 100–199；武器 200–299；耗材/材料 300+（待用）。
 * id 会序列化进存档，一经发布不可改号。
 *
 * @param id    全局物品 id
 * @param key   英文键（与 t_item_def.key_name 对齐）
 * @param cn    中文名
 * @param kind  类别
 * @param block 若为方块则对应的 {@link BlockType}，否则 null
 */
public record Item(int id, String key, String cn, Kind kind, BlockType block) {

    public enum Kind { BLOCK, TOOL, WEAPON, COIN, MATERIAL }

    public boolean placeable() {
        return kind == Kind.BLOCK && block != null && block != BlockType.AIR;
    }

    /** 工具类工厂：由「类型 × 材质」派生（id 段与 Tool 一致：镐 100–105、斧 110–115）。 */
    public static Item pickaxe(Material m) {
        return new Item(100 + m.tier(), "PICKAXE_" + m.name(), m.cn + "镐", Kind.TOOL, null);
    }

    public static Item axe(Material m) {
        return new Item(110 + m.tier(), "AXE_" + m.name(), m.cn + "斧", Kind.TOOL, null);
    }

    /** 便捷命名常量（均由工厂派生，非手写）。 */
    public static final Item WOOD_PICKAXE = pickaxe(Material.WOOD);
    public static final Item WOOD_AXE = axe(Material.WOOD);
    /** 五职业主武器 200–204（未选职业前默认用战士剑作为“职业主武器”）。 */
    public static final Item WARRIOR_SWORD = new Item(200, "WEAPON_WARRIOR", "战士主武器", Kind.WEAPON, null);
    public static final Item MAGE_WAND = new Item(201, "WEAPON_MAGE", "法师主武器", Kind.WEAPON, null);
    public static final Item SUMMONER_STAFF = new Item(202, "WEAPON_SUMMONER", "通灵者主武器", Kind.WEAPON, null);
    public static final Item ARCHER_BOW = new Item(203, "WEAPON_ARCHER", "射手主武器", Kind.WEAPON, null);
    public static final Item ASSASSIN_DAGGER = new Item(204, "WEAPON_ASSASSIN", "刺客主武器", Kind.WEAPON, null);
    /** 铸梦币（硬通货，拾取即入账、不占背包格）；id 段 400。 */
    public static final Item COIN = new Item(400, "COIN", "铸梦币", Kind.COIN, null);

    /** 金属锭（熔炉冶炼矿石所得）；材料 id 段 300+。 */
    public static final Item IRON_INGOT = new Item(300, "IRON_INGOT", "铁锭", Kind.MATERIAL, null);
    public static final Item GOLD_INGOT = new Item(301, "GOLD_INGOT", "金锭", Kind.MATERIAL, null);
    public static final Item PLATINUM_INGOT = new Item(302, "PLATINUM_INGOT", "白金锭", Kind.MATERIAL, null);
    public static final Item SAINT_INGOT = new Item(303, "SAINT_INGOT", "圣锭", Kind.MATERIAL, null);
    private static final Item[] MATERIALS = {IRON_INGOT, GOLD_INGOT, PLATINUM_INGOT, SAINT_INGOT};

    /** 起始三件套：木镐 + 木斧 + 职业主武器（默认战士剑）。 */
    public static final Item[] STARTER_KIT = {WOOD_PICKAXE, WOOD_AXE, WARRIOR_SWORD};

    private static final Item[] WEAPONS = {WARRIOR_SWORD, MAGE_WAND, SUMMONER_STAFF, ARCHER_BOW, ASSASSIN_DAGGER};

    private static final Map<Integer, Item> BY_ID = new HashMap<>();
    private static final Map<String, Item> BY_KEY = new HashMap<>();

    static {
        for (BlockType b : BlockType.values()) {
            register(new Item(b.id(), b.name(), b.cn(), Kind.BLOCK, b));
        }
        for (Material m : Material.values()) {   // 工具由材质派生，与 Tool 的 id 段一致
            register(pickaxe(m));
            register(axe(m));
        }
        for (Item it : WEAPONS) {
            register(it);
        }
        register(COIN);
        for (Item it : MATERIALS) {
            register(it);
        }
    }

    private static void register(Item it) {
        BY_ID.put(it.id, it);
        BY_KEY.put(it.key, it);
    }

    public static Item byId(int id) {
        return BY_ID.get(id);
    }

    public static Item byKey(String key) {
        return BY_KEY.get(key);
    }

    /** 由方块构造物品（保证与注册表同 id）。 */
    public static Item ofBlock(BlockType b) {
        return BY_ID.get((int) b.id());   // 强转 int：避免 short 装箱与 Integer 键不等
    }
}
