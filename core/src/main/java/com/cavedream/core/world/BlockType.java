package com.cavedream.core.world;

/**
 * 方块类型表（MVP 占位版）。
 * id 序列化进层存档，禁止改号；颜色为占位像素色（正式美术见 GDD §9）。
 */
public enum BlockType {

    AIR((short) 0, false, 0x000000),
    DIRT((short) 1, true, 0x6B4A2F),
    GRASS((short) 2, true, 0x4C8A3E),
    STONE((short) 3, true, 0x55525E),
    DEEP_SEED((short) 4, true, 0x49C7C9),   // 沉眠矿：梦境主题基础矿（GDD Q14 定名）
    WOOD((short) 5, true, 0x8A6234),
    LEAF((short) 6, true, 0x2F6B2A),
    FOG((short) 7, true, 0x2A2140);         // 梦之雾：箱庭边界软墙（GDD §2.3）

    private static final BlockType[] BY_ID = new BlockType[16];

    static {
        for (BlockType t : values()) {
            BY_ID[t.id] = t;
        }
    }

    private final short id;
    private final boolean solid;
    private final int rgb;

    BlockType(short id, boolean solid, int rgb) {
        this.id = id;
        this.solid = solid;
        this.rgb = rgb;
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
