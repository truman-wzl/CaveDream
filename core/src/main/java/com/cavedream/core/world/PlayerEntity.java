package com.cavedream.core.world;

/**
 * 玩家实体：纯逻辑 AABB  tile 碰撞（无渲染依赖，可单测）。
 * 坐标 = 像素，(x,y) 为碰撞盒左下角，y 轴向上（与 {@link LayerWorld} 约定一致）。
 */
public final class PlayerEntity {

    public static final int TILE = 16;

    private static final float MOVE_SPEED = 170f;
    private static final float JUMP_VELOCITY = 500f;   // 初始跳跃高≈5.5 格（v²/2g=500²/2800≈89px）
    private static final float GRAVITY = 1400f;
    private static final float MAX_FALL = 700f;
    private static final float EPS = 0.01f;

    private float x;
    private float y;
    /** 碰撞盒定稿（GDD §2.4/Q9，v0.17）：宽 1.3 格 × 高 2.6 格，1 格竖井不可穿过 */
    private final float width = 1.3f * TILE;
    private final float height = 2.6f * TILE;
    private float vx;
    private float vy;
    private boolean onGround;
    private float speedScale = 1f;   // 移速倍率（濒死时 0.4）
    private float gravityScale = 1f; // 重力倍率（天空带降低→跳得更高）
    /** 面向：1 右 / -1 左（贴图默认朝右，渲染时据此镜像） */
    private int facing = 1;

    public PlayerEntity(float x, float y) {
        this.x = x;
        this.y = y;
    }

    /** 直接设定位置（读档/复活用）。 */
    public void setPos(float nx, float ny) {
        this.x = nx;
        this.y = ny;
        this.vx = 0f;
        this.vy = 0f;
    }

    /** 移速倍率（濒死降速）。 */
    public void setSpeedScale(float s) {
        this.speedScale = s;
    }

    /** 重力倍率（天空带用较低值使跳跃更高）。 */
    public void setGravityScale(float s) {
        this.gravityScale = s;
    }

    /**
     * 推进一帧。
     * @param dt  秒
     * @param jump 本帧是否请求起跳（按住即自动跳，由调用方决定语义）
     */
    public void update(LayerWorld world, boolean left, boolean right, boolean jump, float dt) {
        dt = Math.min(dt, 1f / 30f);   // 防卡顿大跳穿墙

        vx = ((right ? MOVE_SPEED : 0f) + (left ? -MOVE_SPEED : 0f)) * speedScale;
        if (vx > 0) {
            facing = 1;
        } else if (vx < 0) {
            facing = -1;
        }
        if (jump && onGround) {
            vy = JUMP_VELOCITY;
            onGround = false;
        }
        vy = Math.max(vy - GRAVITY * gravityScale * dt, -MAX_FALL);

        // X 轴移动 + 碰撞回弹
        x += vx * dt;
        if (overlapsSolid(world)) {
            if (vx > 0) {
                x = (float) Math.floor((x + width) / TILE) * TILE - width - EPS;
            } else if (vx < 0) {
                x = (float) (Math.floor(x / TILE) + 1) * TILE + EPS;
            }
            vx = 0;
        }

        // Y 轴移动 + 落地/顶头
        float prevBottom = y;
        y += vy * dt;
        onGround = false;
        if (overlapsSolid(world)) {
            if (vy < 0) {
                y = (float) (Math.floor(y / TILE) + 1) * TILE + EPS;
                onGround = true;
            } else if (vy > 0) {
                y = (float) Math.floor((y + height) / TILE) * TILE - height - EPS;
            }
            vy = 0;
        } else if (vy < 0) {
            // 单向平台：下落时若脚从上方跨过平台顶面则站上去（不阻挡水平/向上→可穿过）
            int fx0 = (int) Math.floor(x / TILE);
            int fx1 = (int) Math.floor((x + width) / TILE);
            int fy = (int) Math.floor(y / TILE);
            for (int tx = fx0; tx <= fx1; tx++) {
                if (world.isPlatform(tx, fy) && prevBottom >= (fy + 1) * TILE - 0.5f) {
                    y = (fy + 1) * TILE + EPS;
                    onGround = true;
                    vy = 0f;
                    break;
                }
            }
        }
    }

    /** 碰撞盒覆盖的所有 tile 是否有实心。 */
    public boolean overlapsSolid(LayerWorld world) {
        int x0 = (int) Math.floor(x / TILE);
        int x1 = (int) Math.floor((x + width) / TILE);
        int y0 = (int) Math.floor(y / TILE);
        int y1 = (int) Math.floor((y + height) / TILE);
        for (int tx = x0; tx <= x1; tx++) {
            for (int ty = y0; ty <= y1; ty++) {
                if (world.isSolid(tx, ty)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 碰撞盒是否与某 tile 格重叠（放置方块防卡玩家用）。 */
    public boolean overlapsTile(int tileX, int tileY) {
        return x < (tileX + 1) * TILE && x + width > tileX * TILE
                && y < (tileY + 1) * TILE && y + height > tileY * TILE;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float width() {
        return width;
    }

    public float height() {
        return height;
    }

    public float centerX() {
        return x + width / 2;
    }

    public float centerY() {
        return y + height / 2;
    }

    public boolean isOnGround() {
        return onGround;
    }

    /** 本帧水平是否移动（驱动走路动画）。 */
    public boolean isMoving() {
        return vx != 0f;
    }

    /** 垂直速度（驱动跳跃/下落姿态）。 */
    public float vy() {
        return vy;
    }

    /** 当前面向：1 右 / -1 左。 */
    public int facing() {
        return facing;
    }

    /** 设定面向（非负=右、负=左）；挥武器时按鼠标与角色相对方位自动转身。 */
    public void setFacing(int f) {
        facing = f >= 0 ? 1 : -1;
    }
}
