package com.cavedream.core.world.mob;

import java.util.List;

/**
 * 召唤仆从（通灵者/召唤师的光球宠物）：悬浮跟随玩家，自动锁定最近的怪并飞上去持续攻击；
 * 有自身寿命与"生命"，被时间消耗到期消失。纯逻辑、可单测（不依赖渲染）。
 */
public class Servant {

    private static final float FOLLOW_SPEED = 300f;
    private static final float ATTACK_SPEED = 240f;
    private static final float CONTACT = 16f;      // 命中距离（像素）
    private static final float RANGE = 18f * 16f;  // 索敌半径（像素，18 格）

    private float x;
    private float y;
    private int hp;
    private final int maxHp;
    private float life;
    private final int dmg;
    private float atkCd;

    public Servant(float x, float y, int maxHp, int dmg, float life) {
        this.x = x;
        this.y = y;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.dmg = dmg;
        this.life = life;
    }

    /** 每帧：索敌→飞向并接触攻击；无怪则绕玩家悬停。返回是否仍存活。 */
    public boolean update(float pcx, float pcy, List<Mob> mobs, float dt) {
        dt = Math.min(dt, 1f / 30f);
        life -= dt;
        atkCd -= dt;
        Mob target = nearestMob(pcx, pcy, mobs);
        float tx, ty, speed;
        if (target != null) {
            tx = target.centerX();
            ty = target.centerY();
            speed = ATTACK_SPEED;
            if (Math.hypot(tx - x, ty - y) <= CONTACT && atkCd <= 0f) {
                target.damage(dmg);
                atkCd = 0.45f;
            }
        } else {
            tx = pcx + 26f;                       // 悬停在玩家肩侧
            ty = pcy + 22f;
            speed = FOLLOW_SPEED;
        }
        float dx = tx - x, dy = ty - y;
        float d = (float) Math.hypot(dx, dy);
        if (d > 1f) {
            x += dx / d * Math.min(speed * dt, d);
            y += dy / d * Math.min(speed * dt, d);
        }
        return life > 0f && hp > 0;
    }

    private Mob nearestMob(float pcx, float pcy, List<Mob> mobs) {
        Mob best = null;
        float bd = RANGE;
        for (Mob m : mobs) {
            if (!m.isAlive()) {
                continue;
            }
            float d = (float) Math.hypot(m.centerX() - pcx, m.centerY() - pcy);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    /** 到期或受击可扣血（预留给敌人反击仆从）。 */
    public boolean damage(int amount) {
        hp = Math.max(0, hp - amount);
        return hp == 0;
    }

    public void refreshLife(float seconds) {
        life = Math.max(life, seconds);
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public int hp() {
        return hp;
    }

    public int maxHp() {
        return maxHp;
    }

    public float life() {
        return life;
    }
}
