package com.cavedream.core.world.mob;

import java.util.List;

/**
 * 召唤仆从（通灵者/召唤师的光球宠物）：环绕玩家飞行，自动锁定最近的怪并飞上去持续攻击。
 * 不自动消失（永久跟随，直到玩家手动收起）；不占模型。纯逻辑、可单测。
 */
public class Servant {

    private static final float FOLLOW_SPEED = 320f;
    private static final float ATTACK_SPEED = 260f;
    private static final float CONTACT = 16f;      // 命中距离（像素）
    private static final float ORBIT_R = 30f;      // 环绕半径（像素）

    private float x;
    private float y;
    private final int maxHp;
    private int hp;
    private final int dmg;
    private final float orbitPhase;                // 多只仆从按相位错开环绕
    private float t;
    private float atkCd;

    public Servant(float x, float y, int maxHp, int dmg, float orbitPhase) {
        this.x = x;
        this.y = y;
        this.maxHp = maxHp;
        this.hp = maxHp;
        this.dmg = dmg;
        this.orbitPhase = orbitPhase;
    }

    /** 旧 4 参重载：不限视框（索敌任意最近怪），供单测与无相机上下文使用。 */
    public boolean update(float pcx, float pcy, List<Mob> mobs, float dt) {
        return update(pcx, pcy, mobs, dt, pcx, pcy, Float.MAX_VALUE / 4f, Float.MAX_VALUE / 4f);
    }

    /** 每帧：锁定**视框内**(viewCx/Cy ± half)最近的怪→飞近接触攻击；框内无怪→绕玩家相位环绕。永不因时间消失。 */
    public boolean update(float pcx, float pcy, List<Mob> mobs, float dt,
                          float viewCx, float viewCy, float viewHalfW, float viewHalfH) {
        dt = Math.min(dt, 1f / 30f);
        t += dt;
        atkCd -= dt;
        Mob target = nearestInView(pcx, pcy, mobs, viewCx, viewCy, viewHalfW, viewHalfH);
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
            double ang = orbitPhase + t * 1.6f;                 // 绕玩家旋转
            tx = pcx + (float) Math.cos(ang) * ORBIT_R;
            ty = pcy + (float) Math.sin(ang) * ORBIT_R;
            speed = FOLLOW_SPEED;
        }
        float dx = tx - x, dy = ty - y;
        float d = (float) Math.hypot(dx, dy);
        if (d > 1f) {
            x += dx / d * Math.min(speed * dt, d);
            y += dy / d * Math.min(speed * dt, d);
        }
        return hp > 0;
    }

    /** 视框内、距玩家最近的存活怪（框外一律忽略）；无则 null。 */
    private Mob nearestInView(float pcx, float pcy, List<Mob> mobs,
                             float viewCx, float viewCy, float viewHalfW, float viewHalfH) {
        Mob best = null;
        float bd = Float.MAX_VALUE;
        for (Mob m : mobs) {
            if (!m.isAlive()) {
                continue;
            }
            if (Math.abs(m.centerX() - viewCx) > viewHalfW || Math.abs(m.centerY() - viewCy) > viewHalfH) {
                continue;   // 不在视框内→不索敌
            }
            float d = (float) Math.hypot(m.centerX() - pcx, m.centerY() - pcy);
            if (d < bd) {
                bd = d;
                best = m;
            }
        }
        return best;
    }

    public boolean damage(int amount) {
        hp = Math.max(0, hp - amount);
        return hp == 0;
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
}
