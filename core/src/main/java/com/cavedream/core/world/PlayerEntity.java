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

    /** 摔落伤害：按落地“冲击速度”结算（对重力缩放免疫→正常跳跃、天空带低重力跳都不会误伤）。 */
    public static final float SAFE_IMPACT_SPEED = 580f;  // 略高于跳跃落地速度(~500)：低于此不受伤
    public static final float FALL_DMG_PER_SPEED = 1.0f; // 每超 1 点速度 1 点伤害
    public static final int FALL_DMG_CAP = 100;          // 单次上限（终端速度坠亡≈致死）
    
    /** 梦水（液体）物理：中性浮力→无输入即悬浮不动；WASD 全向游动；头露出水面时按 W 跃出、随后回落入水（梦境不溺水）。 */
    private static final float WATER_MOVE_SCALE = 0.62f; // 水中水平游速（相对常速的阻力衰减）
    private static final float SWIM_SPEED = 130f;        // 水中上下游动速度（W 上 / S 下）

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

    private boolean airborne;         // 是否腾空（用于识别“落地那一帧”）
    private int pendingFallDamage;    // 落地结算出的坠落伤害（待上层取用）
    private boolean inWater;          // 碰撞盒是否浸在梦水中（驱动游泳物理与形态）

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
        this.airborne = false;
        this.pendingFallDamage = 0;
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
        update(world, left, right, jump, false, dt);
    }

    /**
     * 推进一帧（含水中下行输入）。
     * @param down 水中按下→下潜；陆上无意义
     */
    public void update(LayerWorld world, boolean left, boolean right, boolean jump, boolean down, float dt) {
        dt = Math.min(dt, 1f / 30f);   // 防卡顿大跳穿墙

        inWater = overlapsLiquid(world);
        float speed = MOVE_SPEED * speedScale * (inWater ? WATER_MOVE_SCALE : 1f);
        vx = (right ? speed : 0f) + (left ? -speed : 0f);
        if (vx > 0) {
            facing = 1;
        } else if (vx < 0) {
            facing = -1;
        }
        if (inWater) {
            // 中性浮力：无输入即悬浮不动；W/S 上下、A/D 左右游动（vx 已按阻力设定）
            if (jump && !headInWater(world)) {
                vy = JUMP_VELOCITY;          // 头已露出水面→按 W 跃出水面，出水后由重力回落
                onGround = false;
            } else {
                vy = ((jump ? 1f : 0f) + (down ? -1f : 0f)) * SWIM_SPEED;
            }
            airborne = false;
        } else {
            if (jump && onGround) {
                vy = JUMP_VELOCITY;
                onGround = false;
            }
            vy = Math.max(vy - GRAVITY * gravityScale * dt, -MAX_FALL);
        }

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
        float vyAtImpact = vy;   // 本帧落地前的竖直速度（vy<0=下落），用于按冲击速度算摔落伤害
        y += vy * dt;
        onGround = false;
        boolean landedThisFrame = false;
        if (overlapsSolid(world)) {
            if (vy < 0) {
                y = (float) (Math.floor(y / TILE) + 1) * TILE + EPS;
                onGround = true;
                landedThisFrame = true;
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
                    landedThisFrame = true;
                    break;
                }
            }
        }

        // 摔落伤害：仅在“腾空后落地那一帧”按冲击速度结算（伤害由上层 stats.damage 应用）
        if (onGround) {
            if (airborne && landedThisFrame) {
                float impact = -vyAtImpact;   // 正值下落速度
                if (impact > SAFE_IMPACT_SPEED) {
                    pendingFallDamage = Math.min(
                            (int) ((impact - SAFE_IMPACT_SPEED) * FALL_DMG_PER_SPEED), FALL_DMG_CAP);
                }
            }
            airborne = false;
        } else {
            airborne = true;
        }
    }

    /** 取出并清零本帧落地结算的坠落伤害（无则 0）。 */
    public int consumeFallDamage() {
        int d = pendingFallDamage;
        pendingFallDamage = 0;
        return d;
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

    /** 碰撞盒覆盖格是否有液体（梦水）。 */
    private boolean overlapsLiquid(LayerWorld world) {
        int x0 = (int) Math.floor(x / TILE);
        int x1 = (int) Math.floor((x + width) / TILE);
        int y0 = (int) Math.floor(y / TILE);
        int y1 = (int) Math.floor((y + height) / TILE);
        for (int tx = x0; tx <= x1; tx++) {
            for (int ty = y0; ty <= y1; ty++) {
                if (world.isWater(tx, ty)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 头顶格是否在液体中（false=头已露出水面→可跃出）。 */
    private boolean headInWater(LayerWorld world) {
        int tx = (int) Math.floor(centerX() / TILE);
        int ty = (int) Math.floor((y + height - 1f) / TILE);
        return world.isWater(tx, ty);
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

    /** 是否浸在梦水中（驱动游泳形态渲染）。 */
    public boolean isInWater() {
        return inWater;
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
