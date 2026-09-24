package com.cavedream.core.fx;

import com.cavedream.core.world.LayerWorld;
import com.cavedream.core.world.PlayerEntity;

/**
 * 投射物（远程武器特效基元）：直线飞行、命中固体或超时消失；命中怪由上层结算。
 * 纯逻辑、可单测。kind 决定渲染样式（0 箭 / 1 魔法弹 / 2 召唤球 / 3 飞刀）。
 */
public class Projectile {

    public static final int ARROW = 0, BOLT = 1, ORB = 2, DAGGER = 3;

    public float x;
    public float y;
    public float vx;
    public float vy;
    public int dmg;
    public float life;
    public final int kind;
    public boolean fromPlayer = true;

    public Projectile(float x, float y, float vx, float vy, int dmg, float life, int kind) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.dmg = dmg;
        this.life = life;
        this.kind = kind;
    }

    /** 推进；撞固体或寿命耗尽则 alive=false。重力：飞刀/箭轻微下坠，魔法/召唤球直线。 */
    public boolean update(LayerWorld world, float dt) {
        if (kind == ARROW || kind == DAGGER) {
            vy -= 520f * dt;   // 轻微抛物线
        }
        x += vx * dt;
        y += vy * dt;
        life -= dt;
        int t = PlayerEntity.TILE;
        int cx = (int) Math.floor(x / t), cy = (int) Math.floor(y / t);
        if (!world.inBounds(cx, cy) || world.isSolid(cx, cy)) {
            return false;
        }
        return life > 0f;
    }

    /** 与像素矩形的相交（命中怪判定）。 */
    public boolean hitsRect(float rx, float ry, float rw, float rh) {
        return x >= rx && x <= rx + rw && y >= ry && y <= ry + rh;
    }
}
