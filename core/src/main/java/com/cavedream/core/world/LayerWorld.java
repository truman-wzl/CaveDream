package com.cavedream.core.world;

/**
 * 一层梦的完整世界数据（GDD §2.3 有限箱庭）：整层常驻内存，无 chunk 流式加载。
 * 坐标约定：(0,0) 在左下角，y 轴向上；单位 = tile。
 */
public final class LayerWorld {

    private final int width;
    private final int height;
    private final short[] tiles;
    private final short[] mounts;   // 墙面对象第二层（火把等），0=无

    private int spawnTileX;
    private int spawnTileY;   // 出生点 = 玩家脚所站的空气格（其下方一格为实心）

    public LayerWorld(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("箱庭尺寸必须为正：" + width + "x" + height);
        }
        this.width = width;
        this.height = height;
        this.tiles = new short[width * height];   // 默认全 AIR
        this.mounts = new short[width * height];   // 默认无墙面对象
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

    /** 该格是否“平台”类（可站立、不挡路）。越界=false。 */
    public boolean isPlatform(int x, int y) {
        return inBounds(x, y) && blockAt(x, y).platform();
    }

    /** 该格是否阻挡敌怪（实体或门）；越界=true（雾墙安全网）。 */
    public boolean blocksMob(int x, int y) {
        return !inBounds(x, y) || blockAt(x, y).blocksMobs();
    }

    /** 墙面对象（第二层）：贴于背景墙/实心墙上的非实体装饰（火把等）；无则 AIR。 */
    public BlockType mountAt(int x, int y) {
        if (!inBounds(x, y)) {
            return BlockType.AIR;
        }
        return BlockType.of(mounts[y * width + x]);
    }

    public void setMount(int x, int y, BlockType type) {
        if (inBounds(x, y)) {
            mounts[y * width + x] = type.id();
        }
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
