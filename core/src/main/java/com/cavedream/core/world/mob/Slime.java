package com.cavedream.core.world.mob;

import com.cavedream.core.world.LayerWorld;
import com.cavedream.core.world.PlayerEntity;

import java.util.Random;

/**
 * 史莱姆：会朝玩家方向周期跳跃的初级怪（GDD 首个敌怪）。7 色随机生成。
 * 接触玩家由上层结算伤害；本类只负责移动/跳跃与存活。
 */
public class Slime extends Mob {

    /** 敌怪 id（“一切皆 ID”）。 */
    public static final int ID = 1000;

    /** 7 色史莱姆调色板（RGB）。 */
    public static final int[] PALETTE = {
            0x4C8A3E, 0x2E6FBF, 0xC0392B, 0xE6C34A, 0x8E7CC3, 0xE67E22, 0x49C7C9,
    };
    public static final int COLOR_COUNT = PALETTE.length;

    private static final float W = 22f;
    private static final float H = 18f;
    private static final int MAX_HP = 24;
    private static final float HOP_VX = 78f;
    private static final float HOP_VY_MIN = 500f;   // 跳高≈5.5 格（v²/2g）
    private static final float HOP_VY_RANGE = 60f;   // +至≈7 格

    private final Random rnd;
    private float hopTimer;

    public Slime(float x, float y, int colorIndex, long seed) {
        super(x, y, W, H, MAX_HP, Math.floorMod(colorIndex, COLOR_COUNT));
        this.rnd = new Random(seed);
        this.hopTimer = 0.4f + rnd.nextFloat();
    }

    @Override
    protected void ai(LayerWorld world, float px, float py, float dt) {
        if (onGround) {
            vx = 0f;
            hopTimer -= dt;
            if (hopTimer <= 0f) {
                int dir = px >= centerX() ? 1 : -1;      // 朝玩家跳
                int t = PlayerEntity.TILE;
                int fx = (int) ((dir > 0 ? x + w + 2 : x - 2) / t);
                int fy = (int) ((y + 2) / t);
                boolean wallAhead = world.isSolid(fx, fy) || world.isSolid(fx, fy + 1);   // 前方有障碍→加大起跳翻越
                vx = dir * (wallAhead ? HOP_VX * 1.5f : HOP_VX);
                vy = wallAhead ? HOP_VY_MIN + HOP_VY_RANGE + 130f
                        : HOP_VY_MIN + rnd.nextFloat() * HOP_VY_RANGE;
                onGround = false;
                hopTimer = 0.7f + rnd.nextFloat() * 0.9f;
            }
        }
    }

    @Override
    public int typeId() {
        return ID;
    }

    public int rgb() {
        return PALETTE[colorIndex];
    }

    /** 接触伤害（上层用）。 */
    public int touchDamage() {
        return 8;
    }

    /** 生成点：把史莱姆放到某列地表之上（世界格→像素）。 */
    public static Slime onSurface(LayerWorld world, int tileX, int colorIndex, long seed) {
        int t = PlayerEntity.TILE;
        int topY = world.getHeight() - 1;
        for (int y = world.getHeight() - 1; y >= 0; y--) {
            if (world.isSolid(tileX, y)) {
                topY = y;
                break;
            }
        }
        return onFloor(world, tileX, topY, colorIndex, seed);
    }

    /** 在指定列、已知地面行 floorY 之上放一只史莱姆。 */
    public static Slime onFloor(LayerWorld world, int tileX, int floorY, int colorIndex, long seed) {
        int t = PlayerEntity.TILE;
        float x = tileX * (float) t + (t - W) / 2f;
        float y = (floorY + 1) * (float) t + 1f;
        return new Slime(x, y, colorIndex, seed);
    }
}
