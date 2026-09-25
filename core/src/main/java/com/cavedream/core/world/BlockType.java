package com.cavedream.core.world;

/**
 * 方块类型表（MVP 占位版）。
 * id 序列化进层存档，禁止改号；颜色为占位像素色（正式美术见 GDD §9）。
 */
public enum BlockType {

    AIR((short) 0, false, 0x000000, "空气", 0, 0f),
    DIRT((short) 1, true, 0x6B4A2F, "泥土", 0, 0.45f),
    GRASS((short) 2, true, 0x4C8A3E, "草方块", 0, 0.45f),
    STONE((short) 3, true, 0x55525E, "石头", 0, 0.9f),
    DEEP_SEED((short) 4, true, 0x49C7C9, "沉眠矿", 6, 1.3f),   // 梦境主题基础矿（GDD Q14 定名），自发光
    WOOD((short) 5, true, 0x8A6234, "木材", 0, 0.6f),
    LEAF((short) 6, true, 0x2F6B2A, "树叶", 0, 0.25f),
    FOG((short) 7, true, 0x2A2140, "梦之雾", 1, -1f),       // 箱庭边界软墙（GDD §2.3），不可挖
    WATER((short) 8, false, 0x2E6FBF, "梦水", 0, -1f),      // 一海（非实体），不可挖
    HELL((short) 9, true, 0x3A1F1F, "梦滓", 3, 1.6f),        // 地狱带：梦的残渣，微光
    MUSHROOM((short) 10, true, 0x7E5AA0, "幻菇", 5, 0.4f),   // 蘑菇生物群系植被，自发光
    CLOUD((short) 11, true, 0xB9A7E0, "云絮", 0, 0.3f),      // 天空带浮岛主体
    POT((short) 12, true, 0xB0703F, "陶罐", 0, 0.35f),       // 可砸：铸梦币小抽奖（§3.5）
    IRON_ORE((short) 13, true, 0x9AA0A8, "铁矿", 0, 1.5f),
    GOLD_ORE((short) 14, true, 0xD9A94A, "金矿", 0, 1.8f),
    PLATINUM_ORE((short) 15, true, 0xDCE6EF, "白金矿", 0, 2.2f),
    SAINT_ORE((short) 16, true, 0xB46BE6, "圣矿", 8, 2.8f),  // 圣矿自发光（顶层稀有矿）
    DOOR((short) 17, false, 0x6E4A28, "木门", 0, 0.5f),   // 可穿过的木门（村庄/房屋用）
    PLATFORM((short) 18, false, 0xB0824A, "木平台", 0, 0.3f),   // 可站立、不挡路（家具基元）
    TABLE((short) 19, false, 0x9A6A38, "桌子", 0, 0.4f),        // 平台类家具（不挡路）
    CHAIR((short) 20, false, 0x8A5A2B, "椅子", 0, 0.35f),       // 平台类家具
    WORKBENCH((short) 21, false, 0x6E4A28, "工作台", 0, 0.5f),   // 平台类家具（合成站）
    BED((short) 22, false, 0xB04A5A, "床", 0, 0.5f),           // 平台类家具（出生点）
    FURNACE((short) 23, true, 0x3A3A44, "熔炉", 4, 1.0f),    // 冶炼金属锭的工作站（实体、自发光）
    CHEST((short) 24, true, 0x8A5A2B, "木箱", 0, 0.5f),     // 存储家具（右键开箱存取，内容随存档持久）
    TORCH((short) 25, false, 0xE0A030, "火把", 6, 0.2f),    // 非实体光源（不挡路，房子/照明用）
    WOOD_WALL((short) 26, false, 0x6E5230, "木墙", 0, 0.4f), // 背景墙：非实体、可穿行，铺满屋内→合法房屋“贴满墙”要件
    TREE((short) 27, false, 0x3E7A2E, "树", 0, 1.2f);        // 树：非实体整体对象（3宽×6~16高），仅底部中间格可用斧砍倒

    private static final BlockType[] BY_ID = new BlockType[32];

    static {
        for (BlockType t : values()) {
            BY_ID[t.id] = t;
        }
    }

    private final short id;
    private final boolean solid;
    private final int rgb;
    private final String cn;
    private final int light;   // 自发光等级 0~15（16 级制，0=不发光）
    private final float digSeconds;   // 基准挖掘耗时（秒，按 power=1 初始工具）；负=不可挖

    BlockType(short id, boolean solid, int rgb, String cn, int light, float digSeconds) {
        this.id = id;
        this.solid = solid;
        this.rgb = rgb;
        this.cn = cn;
        this.light = light;
        this.digSeconds = digSeconds;
    }

    /** 自发光等级 0~15（渲染光照的块光源种子）。 */
    public int light() {
        return light;
    }

    /** 基准挖掘耗时（秒）；power=1 的初始工具即此时长，<0 表示不可挖。 */
    public float digSeconds() {
        return digSeconds;
    }

    /** 是否可被破坏。 */
    public boolean mineable() {
        return digSeconds >= 0f && this != AIR;
    }


    /** 界面展示用中文名。 */
    public String cn() {
        return cn;
    }

    public short id() {
        return id;
    }

    public boolean solid() {
        return solid;
    }

    /** 是否为“平台”类：可站立于其上、但不阻挡水平/向上通行（一次性平台）。 */
    public boolean platform() {
        return this == PLATFORM || this == TABLE || this == CHAIR || this == WORKBENCH || this == BED;
    }

    /** 存储容量（>0 即可作为容器、右键存取）；新存储摆件在此登记。 */
    public int storageCapacity() {
        return this == CHEST ? 40 : 0;
    }

    /** 是否阻挡敌怪：实体方块 + 门（门对玩家可穿、对怪是屏障；史莱姆等非穿墙类不能过门）。 */
    public boolean blocksMobs() {
        return solid || this == DOOR;
    }

    /** 背景墙类：非实体、可穿行，但作为“墙”参与房屋判定与阻挡刷怪。 */
    public boolean backgroundWall() {
        return this == WOOD_WALL;
    }

    /** 液体（梦水）：非实体、可行入；玩家受阻力/浮力，可自由游泳不溺水（梦境设定）。 */
    public boolean liquid() {
        return this == WATER;
    }

    /** 可作为“墙面对象”挂到背景墙/实心墙上的非实体装饰（当前：火把）。 */
    public boolean wallMountable() {
        return this == TORCH;
    }

    /** 是否为“树”对象：非实体、整体贴图渲染、仅底部中间格可被斧砍倒。 */
    public boolean isTree() {
        return this == TREE;
    }

    /* 树对象的尺寸约定（生成与摆放/渲染共用）：宽固定 3、高 6~16。 */
    public static final int TREE_W = 3;
    public static final int TREE_MIN_H = 6;
    public static final int TREE_MAX_H = 16;
    public static final int TREE_START_H = 2;   // 刚种下的树苗高

    /** 摆放占地宽（格）：木箱/熔炉/桌子/工作台 2，其余 1。 */
    public int footprintW() {
        return (this == CHEST || this == TABLE || this == WORKBENCH || this == FURNACE) ? 2 : 1;
    }

    /** 摆放占地高（格）：木箱/熔炉 2，其余 1。 */
    public int footprintH() {
        return (this == CHEST || this == FURNACE) ? 2 : 1;
    }

    /** 是否为“熔炉”：自带存储 + 加工（右键开熔铸界面，矿石→金属锭），区别于普通容器。 */
    public boolean isSmelter() {
        return this == FURNACE;
    }

    public int rgb() {
        return rgb;
    }

    public float r() {
        return ((rgb >> 16) & 0xFF) / 255f;
    }

    public float g() {
        return ((rgb >> 8) & 0xFF) / 255f;
    }

    public float b() {
        return (rgb & 0xFF) / 255f;
    }

    public static BlockType of(short id) {
        BlockType t = id >= 0 && id < BY_ID.length ? BY_ID[id] : null;
        if (t == null) {
            throw new IllegalArgumentException("未知方块 id：" + id);
        }
        return t;
    }
}
