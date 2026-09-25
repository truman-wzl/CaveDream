package com.cavedream.core.world.mob;

import com.cavedream.core.world.LayerWorld;

import java.util.Random;

/**
 * 蝙蝠（敌怪 id 1001）：夜间/洞穴出没的飞行怪，无重力，正弦起伏朝玩家头上方俯冲。
 * 与史莱姆（地面跳）形成"空中 vs 地面"差异；接触伤害较低但更难缠。
 */
public class Bat extends Mob {

    /** 敌怪 id（“一切皆 ID”）。 */
    public static final int ID = 1001;

    private static final float W = 16f;
    private static final float H = 12f;
    private static final int MAX_HP = 12;
    private static final float SPEED = 95f;

    private final Random rnd;
    private float phase;
    private float flap;

    public Bat(float x, float y, long seed) {
        super(x, y, W, H, MAX_HP, 0);
        this.rnd = new Random(seed);
        this.gravityScale = 0f;                 // 飞行：不受重力
        this.coinMin = 3;                       // 掉 3~6 枚铸梦币
        this.coinMax = 6;
        this.phase = rnd.nextFloat() * 6.28f;
    }

    @Override
    protected void ai(LayerWorld world, float px, float py, float dt) {
        phase += dt * 6f;
        flap += dt;
        float dx = px - centerX();
        float dy = (py + 26f) - centerY();      // 想悬停在玩家头顶
        float len = (float) Math.hypot(dx, dy);
        if (len > 1f) {
            vx = dx / len * SPEED;
            vy = dy / len * SPEED;
        } else {
            vx = 0f;
            vy = 0f;
        }
        vy += (float) Math.sin(phase) * 45f;     // 上下起伏
    }

    @Override
    public int typeId() {
        return ID;
    }

    /** 接触伤害（上层用）。 */
    public int touchDamage() {
        return 5;
    }

    /** 翅膀相位（供渲染扇动）。 */
    public float flap() {
        return flap;
    }
}
