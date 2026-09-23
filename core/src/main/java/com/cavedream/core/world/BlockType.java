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
    SAINT_ORE((short) 16, true, 0xB46BE6, "圣矿", 8, 2.8f);  // 圣矿自发光（顶层稀有矿）

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
