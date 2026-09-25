package com.cavedream.core.world;

import com.cavedream.core.item.Item;

/**
 * 掉落物实体（GDD：任何方块/物品被挖/掉落后成为地上的模型，走近吸附拾取——参考 MC/泰拉瑞亚）。
 * 纯逻辑：受重力、与世界方块 AABB 碰撞、地面摩擦；拾取/吸附由上层驱动。坐标为像素，(x,y) 左下，y 轴向上。
 */
public final class ItemDrop {

    public static final int SIZE = 8;              // 拾取盒边长（px）
    private static final float GRAVITY = 900f;
    private static final float MAX_FALL = 600f;
    private static final float EPS = 0.01f;

    private float x;
    private float y;
    private float vx;
    private float vy;
    private boolean onGround;
    private final Item item;
    private int count;
    private float age;
    private boolean dead;

    public ItemDrop(float x, float y, Item item, int count) {
        this.x = x;
        this.y = y;
        this.item = item;
        this.count = count;
    }

    /** 推进物理一帧（dt 秒）。 */
    public void update(LayerWorld world, float dt) {
        dt = Math.min(dt, 1f / 30f);
        age += dt;
        if (!onGround) {
            vy = Math.max(vy - GRAVITY * dt, -MAX_FALL);
        }
        // X
        x += vx * dt;
        if (overlapsSolid(world)) {
            x -= vx * dt;
            vx = 0f;
        }
        // Y
        boolean wasGround = onGround;
        y += vy * dt;
        onGround = false;
        if (overlapsSolid(world)) {
            if (vy < 0f) {
                y = (float) (Math.floor(y / PlayerEntity.TILE) + 1) * PlayerEntity.TILE + EPS;
                onGround = true;
            } else {
                y -= vy * dt;
            }
            vy = 0f;
        }
        if (onGround) {
            vx *= 0.8f;   // 地面摩擦
        } else if (wasGround && vy < 0f) {
            // 走下台阶开始下落
        }
    }

    private boolean overlapsSolid(LayerWorld world) {
        int t = PlayerEntity.TILE;
        int x0 = (int) Math.floor(x / t);
        int x1 = (int) Math.floor((x + SIZE) / t);
        int y0 = (int) Math.floor(y / t);
        int y1 = (int) Math.floor((y + SIZE) / t);
        for (int tx = x0; tx <= x1; tx++) {
            for (int ty = y0; ty <= y1; ty++) {
                if (world.isSolid(tx, ty)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 与给定像素矩形是否相交（用于玩家拾取判定）。 */
    public boolean overlapsRect(float rx, float ry, float rw, float rh) {
        return x < rx + rw && x + SIZE > rx && y < ry + rh && y + SIZE > ry;
    }

    /** 向目标点吸附（朝玩家加速）。 */
    public void seek(float tx, float ty, float speed) {
        float cx = x + SIZE / 2f, cy = y + SIZE / 2f;
        float dx = tx - cx, dy = ty - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len > 0.001f) {
            vx = dx / len * speed;
            vy = dy / len * speed;
            onGround = false;
        }
    }

    /** 磁吸飞行：直接朝目标平移、**忽略世界碰撞**（可穿墙），避免卡在墙后收不到。 */
    public void glideTo(float tx, float ty, float speed, float dt) {
        dt = Math.min(dt, 1f / 30f);
        age += dt;
        float cx = x + SIZE / 2f, cy = y + SIZE / 2f;
        float dx = tx - cx, dy = ty - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len > 0.001f) {
            float step = Math.min(len, speed * dt);
            x += dx / len * step;
            y += dy / len * step;
        }
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float centerX() {
        return x + SIZE / 2f;
    }

    public float centerY() {
        return y + SIZE / 2f;
    }

    public Item item() {
        return item;
    }

    public int count() {
        return count;
    }

    public float age() {
        return age;
    }

    public void addCount(int n) {
        count += n;
    }

    public boolean isDead() {
        return dead;
    }

    public void markDead() {
        dead = true;
    }
}
