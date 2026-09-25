package com.cavedream.core.anim.rig;

import com.cavedream.core.anim.BoneId;

/**
 * 蒙皮绑定：像素网格中每个不透明格绑定到 1~2 根骨 + 权重（线性混合蒙皮 LBS 的数据）。
 * 通用、与实体无关——角色/武器/怪物都用同一结构（配合各自的 {@link BoneId} 与绑定器），
 * 是"万物皆可骨骼化"的地基。纯数据、可单测。
 * 索引 = {@code y * w + x}；未绑定的格（透明）{@link #bound} 为 false。
 */
public final class SkinBinding {

    public final int w;
    public final int h;
    public final boolean[] bound;
    public final byte[] boneA;     // 主骨索引（BoneId.ordinal）
    public final byte[] boneB;     // 副骨索引
    public final float[] wA;       // 主骨权重
    public final float[] wB;       // 副骨权重（wA + wB == 1）

    public SkinBinding(int w, int h) {
        this.w = w;
        this.h = h;
        int n = w * h;
        bound = new boolean[n];
        boneA = new byte[n];
        boneB = new byte[n];
        wA = new float[n];
        wB = new float[n];
    }

    public int index(int x, int y) {
        return y * w + x;
    }

    /** 写入绑定（自动保证权重和为 1，主骨权重不小于副骨）。 */
    public void set(int i, BoneId a, float wa, BoneId b, float wb) {
        float sum = wa + wb;
        if (sum <= 0f) {
            wa = 1f;
            wb = 0f;
            sum = 1f;
        }
        wa /= sum;
        wb /= sum;
        if (wb > wa) {           // 保证 boneA 为主
            float t = wa;
            wa = wb;
            wb = t;
            BoneId tb = a;
            a = b;
            b = tb;
        }
        bound[i] = true;
        boneA[i] = (byte) a.index();
        boneB[i] = (byte) b.index();
        wA[i] = wa;
        wB[i] = wb;
    }

    public int boundCount() {
        int c = 0;
        for (boolean b : bound) {
            if (b) {
                c++;
            }
        }
        return c;
    }

    public BoneId boneA(int i) {
        return BoneId.values()[boneA[i] & 0xFF];
    }

    public BoneId boneB(int i) {
        return BoneId.values()[boneB[i] & 0xFF];
    }
}
