package com.cavedream.core.world.mob;

import com.cavedream.core.world.LayerWorld;
import com.cavedream.core.world.PlayerEntity;

/**
 * 敌人基类（抽象）：共享重力 + 与世界方块的 AABB 碰撞物理；具体行为由子类 {@link #ai} 实现（多态）。
 * 坐标为像素，(x,y) 左下角，y 轴向上，与 {@link LayerWorld}/{@link PlayerEntity} 一致。纯逻辑、可单测。
 */
public abstract class Mob {

    protected static final float GRAVITY = 1400f;
    protected static final float MAX_FALL = 700f;
    protected static final float EPS = 0.01f;
    protected static final float KNOCKBACK_X = 190f;   // 受击水平击退
    protected static final float KNOCKBACK_Y = 150f;   // 受击轻微起跳

    protected float x;
    protected float y;
    protected float vx;
    protected float vy;
    protected final float w;
    protected final float h;
    protected int hp;
    protected final int maxHp;
    protected final int colorIndex;
    protected boolean onGround;
    protected boolean alive = true;
    protected float invulnT;                                                  // 受击无敌帧计时（防灌伤）
    private static final float IFRAME = 0.5f;                                 // 无敌帧时长（秒）
    protected int coinMin = 1;                                                // 死亡掉落铸梦币下限（子类按强度设定）
    protected int coinMax = 3;                                                // 上限

    protected Mob(float x, float y, float w, float h, int maxHp, int colorIndex) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.colorIndex = colorIndex;
    }

    /** 每帧推进：先物理，再子类 AI。px/py 为玩家中心（供追踪/攻击判定）。 */
    public void update(LayerWorld world, float px, float py, float dt) {
        dt = Math.min(dt, 1f / 30f);
        if (invulnT > 0f) {
            invulnT -= dt;
        }
        physics(world, dt);
        ai(world, px, py, dt);
    }

    private void physics(LayerWorld world, float dt) {
        vy = Math.max(vy - GRAVITY * dt, -MAX_FALL);
        x += vx * dt;
        if (overlapsSolid(world)) {
            x -= vx * dt;
            vx = 0f;
        }
        y += vy * dt;
        onGround = false;
        if (overlapsSolid(world)) {
            if (vy < 0) {
                y = (float) (Math.floor(y / PlayerEntity.TILE) + 1) * PlayerEntity.TILE + EPS;
                onGround = true;
            } else {
                y -= vy * dt;
            }
            vy = 0f;
        }
    }

    /** 子类行为（跳跃/巡逻/攻击等）。 */
    protected abstract void ai(LayerWorld world, float px, float py, float dt);

    /** 敌怪类型 id（“一切皆 ID”）：史莱姆=1000，后续怪顺延。 */
    public abstract int typeId();

    /** 本怪一次死亡掉落的铸梦币数（coinMin~coinMax 随机，随怪物类型而变）。 */
    public int rollCoins() {
        return coinMin + (int) (Math.random() * (coinMax - coinMin + 1));
    }

    private boolean overlapsSolid(LayerWorld world) {
        int t = PlayerEntity.TILE;
        int x0 = (int) Math.floor(x / t);
        int x1 = (int) Math.floor((x + w) / t);
        int y0 = (int) Math.floor(y / t);
        int y1 = (int) Math.floor((y + h) / t);
        for (int tx = x0; tx <= x1; tx++) {
            for (int ty = y0; ty <= y1; ty++) {
                if (world.isSolid(tx, ty)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 与给定像素矩形是否相交（玩家接触/攻击判定）。 */
    public boolean overlapsRect(float rx, float ry, float rw, float rh) {
        return x < rx + rw && x + w > rx && y < ry + rh && y + h > ry;
    }

    /** 受伤；归零则死亡。返回是否致死。无敌帧内不受伤（防灌伤）。 */
    public boolean damage(int amount) {
        if (amount <= 0 || !alive || invulnT > 0f) {
            return false;
        }
        invulnT = IFRAME;
        hp -= amount;
        if (hp <= 0) {
            hp = 0;
            alive = false;
            return true;
        }
        return false;
    }

    /** 受伤 + 击退（knockDirX 为击退方向 -1/1）；未致死则给水平冲量 + 小跳起。 */
    public boolean hurt(int amount, float knockDirX) {
        boolean died = damage(amount);
        if (!died) {
            vx += knockDirX * KNOCKBACK_X;
            if (onGround) {
                vy = KNOCKBACK_Y;
                onGround = false;
            }
        }
        return died;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float w() {
        return w;
    }

    public float h() {
        return h;
    }

    public float centerX() {
        return x + w / 2;
    }

    public float centerY() {
        return y + h / 2;
    }

    public int hp() {
        return hp;
    }

    public int maxHp() {
        return maxHp;
    }

    public int colorIndex() {
        return colorIndex;
    }

    public boolean isAlive() {
        return alive;
    }

    /** 是否处于受击无敌帧（供渲染闪白）。 */
    public boolean isInvulnerable() {
        return invulnT > 0f;
    }

    public boolean isOnGround() {
        return onGround;
    }
}
