package com.cavedream.core.world;

/**
 * 一层梦的完整世界数据（GDD §2.3 有限箱庭）：整层常驻内存，无 chunk 流式加载。
 * 坐标约定：(0,0) 在左下角，y 轴向上；单位 = tile。
 */
public final class LayerWorld {

    private final int width;
    private final int height;
    private final short[] tiles;

    private int spawnTileX;
    private int spawnTileY;   // 出生点 = 玩家脚所站的空气格（其下方一格为实心）

    public LayerWorld(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("箱庭尺寸必须为正：" + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.tiles = new short[width * height];   // 默认全 AIR
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    /** 越界按"梦之雾"处理（永远实心，物理安全网）。 */
    public BlockType blockAt(int x, int y) {
        if (!inBounds(x, y)) {
            return BlockType.FOG;
        }
        return BlockType.of(tiles[y * width + x]);
    }

    public void setBlock(int x, int y, BlockType type) {
        if (!inBounds(x, y)) {
            throw new IndexOutOfBoundsException("越界改方块：(" + x + "," + y + ")");
        }
        tiles[y * width + x] = type.id();
    }

    public boolean isSolid(int x, int y) {
        return blockAt(x, y).solid();
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setSpawn(int tileX, int tileY) {
        this.spawnTileX = tileX;
        this.spawnTileY = tileY;
    }

    /** 出生点空气格 x（玩家站立其下格之上）。 */
    public int getSpawnTileX() {
        return spawnTileX;
    }

    /** 出生点空气格 y。 */
    public int getSpawnTileY() {
        return spawnTileY;
    }
}
