package com.cavedream.core.anim;

/**
 * 姿态：每根骨相对 bind 的增量（旋转角度制、平移、缩放）。默认恒等（0,0,0,1,1）。
 * 纯数据、可单测；提供线性混合 {@link #blend} 与叠加 {@link #overlay}（供上半身武器层按骨遮罩叠加）。
 */
public final class Pose {

    public final float[] rot = new float[BoneId.COUNT];
    public final float[] tx = new float[BoneId.COUNT];
    public final float[] ty = new float[BoneId.COUNT];
    public final float[] sx = new float[BoneId.COUNT];
    public final float[] sy = new float[BoneId.COUNT];

    public Pose() {
        reset();
    }

    /** 全恒等（= bind pose）。 */
    public void reset() {
        java.util.Arrays.fill(rot, 0f);
        java.util.Arrays.fill(tx, 0f);
        java.util.Arrays.fill(ty, 0f);
        java.util.Arrays.fill(sx, 1f);
        java.util.Arrays.fill(sy, 1f);
    }

    public void setBone(BoneId bone, float rotDeg, float dx, float dy, float scaleX, float scaleY) {
        int i = bone.index();
        rot[i] = rotDeg;
        tx[i] = dx;
        ty[i] = dy;
        sx[i] = scaleX;
        sy[i] = scaleY;
    }

    public void copyFrom(Pose o) {
        System.arraycopy(o.rot, 0, rot, 0, BoneId.COUNT);
        System.arraycopy(o.tx, 0, tx, 0, BoneId.COUNT);
        System.arraycopy(o.ty, 0, ty, 0, BoneId.COUNT);
        System.arraycopy(o.sx, 0, sx, 0, BoneId.COUNT);
        System.arraycopy(o.sy, 0, sy, 0, BoneId.COUNT);
    }

    /** this = lerp(a, b, t)。 */
    public void blend(Pose a, Pose b, float t) {
        float u = 1f - t;
        for (int i = 0; i < BoneId.COUNT; i++) {
            rot[i] = a.rot[i] * u + b.rot[i] * t;
            tx[i] = a.tx[i] * u + b.tx[i] * t;
            ty[i] = a.ty[i] * u + b.ty[i] * t;
            sx[i] = a.sx[i] * u + b.sx[i] * t;
            sy[i] = a.sy[i] * u + b.sy[i] * t;
        }
    }

    /**
     * 叠加：把 overlay 中 mask 命中的骨，按 weight 混合进 this（用于上半身武器层覆盖在移动层之上）。
     * mask 为 null 表示全身；mask[i]==true 的骨取 {@code lerp(this, overlay, weight)}，其余保持不变。
     */
    public void overlay(Pose overlay, boolean[] mask, float weight) {
        for (int i = 0; i < BoneId.COUNT; i++) {
            if (mask != null && (i >= mask.length || !mask[i])) {
                continue;
            }
            rot[i] += (overlay.rot[i] - rot[i]) * weight;
            tx[i] += (overlay.tx[i] - tx[i]) * weight;
            ty[i] += (overlay.ty[i] - ty[i]) * weight;
            sx[i] += (overlay.sx[i] - sx[i]) * weight;
            sy[i] += (overlay.sy[i] - sy[i]) * weight;
        }
    }

    public boolean isIdentity() {
        for (int i = 0; i < BoneId.COUNT; i++) {
            if (rot[i] != 0f || tx[i] != 0f || ty[i] != 0f || sx[i] != 1f || sy[i] != 1f) {
                return false;
            }
        }
        return true;
    }
}
