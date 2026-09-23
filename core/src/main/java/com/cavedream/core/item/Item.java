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

    public enum Kind { BLOCK, TOOL, WEAPON }

    public boolean placeable() {
        return kind == Kind.BLOCK && block != null && block != BlockType.AIR;
    }

    /** 镐阶梅 100–105（木→圣）。 */
    public static final Item WOOD_PICKAXE = new Item(100, "PICKAXE_WOOD", "木镐", Kind.TOOL, null);
    public static final Item STONE_PICKAXE = new Item(101, "PICKAXE_STONE", "石镐", Kind.TOOL, null);
    public static final Item IRON_PICKAXE = new Item(102, "PICKAXE_IRON", "铁镐", Kind.TOOL, null);
    public static final Item GOLD_PICKAXE = new Item(103, "PICKAXE_GOLD", "金镐", Kind.TOOL, null);
    public static final Item PLATINUM_PICKAXE = new Item(104, "PICKAXE_PLATINUM", "白金镐", Kind.TOOL, null);
    public static final Item SAINT_PICKAXE = new Item(105, "PICKAXE_SAINT", "圣镐", Kind.TOOL, null);
    /** 斧阶梯 110–115（木→圣）。 */
    public static final Item WOOD_AXE = new Item(110, "AXE_WOOD", "木斧", Kind.TOOL, null);
    public static final Item STONE_AXE = new Item(111, "AXE_STONE", "石斧", Kind.TOOL, null);
    public static final Item IRON_AXE = new Item(112, "AXE_IRON", "铁斧", Kind.TOOL, null);
    public static final Item GOLD_AXE = new Item(113, "AXE_GOLD", "金斧", Kind.TOOL, null);
    public static final Item PLATINUM_AXE = new Item(114, "AXE_PLATINUM", "白金斧", Kind.TOOL, null);
    public static final Item SAINT_AXE = new Item(115, "AXE_SAINT", "圣斧", Kind.TOOL, null);
    /** 五职业主武器 200–204（未选职业前默认用战士剑作为“职业主武器”）。 */
    public static final Item WARRIOR_SWORD = new Item(200, "WEAPON_WARRIOR", "战士主武器", Kind.WEAPON, null);
    public static final Item MAGE_WAND = new Item(201, "WEAPON_MAGE", "法师主武器", Kind.WEAPON, null);
    public static final Item SUMMONER_STAFF = new Item(202, "WEAPON_SUMMONER", "通灵者主武器", Kind.WEAPON, null);
    public static final Item ARCHER_BOW = new Item(203, "WEAPON_ARCHER", "射手主武器", Kind.WEAPON, null);
    public static final Item ASSASSIN_DAGGER = new Item(204, "WEAPON_ASSASSIN", "刺客主武器", Kind.WEAPON, null);

    /** 起始三件套：木镐 + 木斧 + 职业主武器（默认战士剑）。 */
    public static final Item[] STARTER_KIT = {WOOD_PICKAXE, WOOD_AXE, WARRIOR_SWORD};

    private static final Item[] TOOLS_WEAPONS = {WOOD_PICKAXE, STONE_PICKAXE, IRON_PICKAXE, GOLD_PICKAXE,
            PLATINUM_PICKAXE, SAINT_PICKAXE, WOOD_AXE, STONE_AXE, IRON_AXE, GOLD_AXE, PLATINUM_AXE, SAINT_AXE,
            WARRIOR_SWORD, MAGE_WAND, SUMMONER_STAFF, ARCHER_BOW, ASSASSIN_DAGGER};

    private static final Map<Integer, Item> BY_ID = new HashMap<>();
    private static final Map<String, Item> BY_KEY = new HashMap<>();

    static {
        for (BlockType b : BlockType.values()) {
            register(new Item(b.id(), b.name(), b.cn(), Kind.BLOCK, b));
        }
        for (Item it : TOOLS_WEAPONS) {
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
