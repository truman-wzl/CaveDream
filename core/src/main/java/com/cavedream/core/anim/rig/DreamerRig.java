package com.cavedream.core.anim.rig;

import com.cavedream.core.anim.BoneId;
import com.cavedream.core.anim.Skeleton;
import com.cavedream.core.anim.Transform2;
import com.cavedream.core.appearance.Humanoid;
import com.cavedream.core.render.PaintedLook;

/**
 * 人形骨架与自动蒙皮（像素坐标 y 向下，0=头顶，H=脚底；尺寸 {@link Humanoid#W}×{@link Humanoid#H}）。
 * 枢轴与部位阈值均以**归一化分数**定义 → 任意分辨率可用（锻造器改尺寸无需动骨架）。
 * 绑定采用通用的"就近双骨 + 逆距权重"：取该格所属肢体链中离它最近的两根骨，按距离倒数分配权重
 * → 关节处自然柔化过渡。此法与实体无关，未来怪物/武器复用同一思路，只换枢轴与候选链。
 */
public final class DreamerRig {

    public static final int W = Humanoid.W, H = Humanoid.H;

    // 骨枢轴（像素，y 向下；由归一化分数 × 画布尺寸得到）
    static final float[] PX = new float[BoneId.COUNT];
    static final float[] PY = new float[BoneId.COUNT];
    static final int[] PARENT = new int[BoneId.COUNT];

    static {
        parent(BoneId.ROOT, null);
        parent(BoneId.HIPS, BoneId.ROOT);
        parent(BoneId.SPINE, BoneId.HIPS);
        parent(BoneId.HEAD, BoneId.SPINE);
        parent(BoneId.HAIR, BoneId.SPINE);
        parent(BoneId.ARM_UB, BoneId.SPINE);
        parent(BoneId.FORE_UB, BoneId.ARM_UB);
        parent(BoneId.HAND_UB, BoneId.FORE_UB);
        parent(BoneId.ARM_FB, BoneId.SPINE);
        parent(BoneId.FORE_FB, BoneId.ARM_FB);
        parent(BoneId.HAND_FB, BoneId.FORE_FB);
        parent(BoneId.THIGH_B, BoneId.HIPS);
        parent(BoneId.SHIN_B, BoneId.THIGH_B);
        parent(BoneId.FOOT_B, BoneId.SHIN_B);
        parent(BoneId.THIGH_F, BoneId.HIPS);
        parent(BoneId.SHIN_F, BoneId.THIGH_F);
        parent(BoneId.FOOT_F, BoneId.SHIN_F);

        // 归一化枢轴（fx, fy ∈ [0,1]）
        pivot(BoneId.ROOT, 0.50f, 0.97f);
        pivot(BoneId.HIPS, 0.50f, 0.70f);
        pivot(BoneId.SPINE, 0.50f, 0.55f);
        pivot(BoneId.HEAD, 0.50f, 0.20f);
        pivot(BoneId.HAIR, 0.22f, 0.28f);
        // 臂链枢轴 = 弯肘 L 形美术的真实关节（上臂竖直 x≈0.30~0.41W、前臂水平 y≈0.45H、拳在前方）
        pivot(BoneId.ARM_UB, 0.36f, 0.335f);   // 肩：上臂顶端
        pivot(BoneId.FORE_UB, 0.31f, 0.46f);   // 肘：上臂底/前臂起端
        pivot(BoneId.HAND_UB, 0.50f, 0.469f);  // 拳头像中心（x 15~17, y 28~32）→ 武器 socket 钉这里
        pivot(BoneId.ARM_FB, 0.64f, 0.335f);
        pivot(BoneId.FORE_FB, 0.62f, 0.46f);
        pivot(BoneId.HAND_FB, 0.719f, 0.469f);  // 后臂拳（x 22~24, y 28~32）
        pivot(BoneId.THIGH_B, 0.40f, 0.70f);
        pivot(BoneId.SHIN_B, 0.40f, 0.82f);
        pivot(BoneId.FOOT_B, 0.40f, 0.94f);
        pivot(BoneId.THIGH_F, 0.58f, 0.70f);
        pivot(BoneId.SHIN_F, 0.58f, 0.82f);
        pivot(BoneId.FOOT_F, 0.62f, 0.94f);
    }

    private static void parent(BoneId c, BoneId p) {
        PARENT[c.index()] = p == null ? -1 : p.index();
    }

    private static void pivot(BoneId b, float fx, float fy) {
        PX[b.index()] = fx * W;
        PY[b.index()] = fy * H;
    }

    /** 构造人形骨架：bind 局部变换=相对父骨的平移（无旋转），故 bind 世界变换的平移即骨枢轴。 */
    public static Skeleton newSkeleton() {
        Transform2[] bindLocal = new Transform2[BoneId.COUNT];
        for (int i = 0; i < BoneId.COUNT; i++) {
            int p = PARENT[i];
            float dx = p < 0 ? PX[i] : PX[i] - PX[p];
            float dy = p < 0 ? PY[i] : PY[i] - PY[p];
            Transform2 t = new Transform2();
            t.setTrs(dx, dy, 0, 1, 1);
            bindLocal[i] = t;
        }
        return new Skeleton(PARENT, bindLocal);
    }

    /** 由锻造后的外观（实例尺寸 + 逐格部位）生成绑定。 */
    public static SkinBinding bind(PaintedLook look) {
        return bind(look.regions(), look.colors, look.w, look.h);
    }

    /**
     * 依 region + 颜色生成逐格绑定：不透明但 region==0 的描边像素按邻域 region 膨胀归类，
     * 再按 {@link #candidates} 逐骨就近双骨赋权。
     */
    public static SkinBinding bind(byte[] regions, int[] colors, int w, int h) {
        SkinBinding sb = new SkinBinding(w, h);
        byte[] eff = new byte[w * h];
        System.arraycopy(regions, 0, eff, 0, Math.min(regions.length, eff.length));
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                boolean opaque = i < colors.length && (colors[i] & 0xFFFFFF) != 0;
                if (opaque && eff[i] == 0) {
                    eff[i] = neighborRegion(regions, w, h, x, y);   // 描边借邻域归类
                }
            }
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (i >= colors.length || (colors[i] & 0xFFFFFF) == 0 || eff[i] == 0) {
                    continue;
                }
                bindNearest2(sb, w, i, candidates(x, y, eff[i], w, h));
            }
        }
        return sb;
    }

    private static byte neighborRegion(byte[] regions, int w, int h, int x, int y) {
        int[][] d = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] k : d) {
            int nx = x + k[0], ny = y + k[1];
            if (nx >= 0 && ny >= 0 && nx < w && ny < h && ny * w + nx < regions.length
                    && regions[ny * w + nx] != 0) {
                return regions[ny * w + nx];
            }
        }
        return 0;
    }

    /** 按 region + 左右/上下（均以分数判定）归到对应肢体骨链（front = x≥中心）。 */
    private static BoneId[] candidates(int x, int y, byte reg, int w, int h) {
        boolean front = x >= w * 0.5f;
        boolean ubArm = x < w * 0.575f;    // 手臂分链：前臂列 0.30~0.5625W（拳右缘 18），后臂列 0.59W（19）起；阈值落在两拳之间
        float fy = y / (float) h;
        switch (reg) {
            case 1:   // 头发：后侧垂发刚性随 HAIR，其余刚性随 HEAD（不混合→动画不拉扯五官）
                return x <= w * 0.35f ? new BoneId[]{BoneId.HAIR} : new BoneId[]{BoneId.HEAD};
            case 2: case 3: case 4:   // 肤/眼/腮：头刚性随 HEAD、颈随 SPINE（单骨、不跨骨混合）
                return fy < 0.40f ? new BoneId[]{BoneId.HEAD} : new BoneId[]{BoneId.SPINE};
            case 5:   // 衣袍 → 躯干（单骨 SPINE，保持上身刚性）
                return new BoneId[]{BoneId.SPINE};
            case 6:   // 手臂（弯肘 L 形）：先按列认拳（刚性、武器靠它），再按行分上臂/前臂（旧阈值是直臂时代的→会把拳劈给上臂）
                if (ubArm) {
                    if (x >= w * 0.455f && fy >= 0.40f) {
                        return new BoneId[]{BoneId.HAND_UB};
                    }
                    return fy < 0.44f ? arr(BoneId.ARM_UB, BoneId.FORE_UB) : arr(BoneId.FORE_UB, BoneId.HAND_UB);
                }
                if (x >= w * 0.68f && fy >= 0.40f) {
                    return new BoneId[]{BoneId.HAND_FB};
                }
                return fy < 0.44f ? arr(BoneId.ARM_FB, BoneId.FORE_FB) : arr(BoneId.FORE_FB, BoneId.HAND_FB);
            case 7:   // 裤 → 大腿/小腿
                if (fy < 0.80f) {
                    return front ? arr(BoneId.THIGH_F, BoneId.SHIN_F) : arr(BoneId.THIGH_B, BoneId.SHIN_B);
                }
                return front ? arr(BoneId.SHIN_F, BoneId.FOOT_F) : arr(BoneId.SHIN_B, BoneId.FOOT_B);
            case 8:   // 鞋 → 脚
                return front ? arr(BoneId.FOOT_F, BoneId.SHIN_F) : arr(BoneId.FOOT_B, BoneId.SHIN_B);
            default:
                return arr(BoneId.SPINE, BoneId.HIPS);
        }
    }

    private static BoneId[] arr(BoneId a, BoneId b) {
        return new BoneId[]{a, b};
    }

    /** 在候选骨里取最近的两个，按逆距离赋权（重合则退化为主骨 1）。 */
    private static void bindNearest2(SkinBinding sb, int w, int i, BoneId[] cand) {
        int n = cand.length;
        if (n == 1) {
            sb.set(i, cand[0], 1f, cand[0], 0f);
            return;
        }
        int x = i % w, y = i / w;
        int bi = -1, bj = -1;
        float di = Float.MAX_VALUE, dj = Float.MAX_VALUE;
        for (BoneId b : cand) {
            float dx = x + 0.5f - PX[b.index()];
            float dy = y + 0.5f - PY[b.index()];
            float d = dx * dx + dy * dy;
            if (d < di) {
                dj = di;
                bj = bi;
                di = d;
                bi = b.index();
            } else if (d < dj) {
                dj = d;
                bj = b.index();
            }
        }
        BoneId a = BoneId.values()[bi];
        BoneId b2 = bj < 0 ? a : BoneId.values()[bj];
        float wi = 1f / (float) (Math.sqrt(di) + 1e-3);
        float wj = bj < 0 ? 0f : 1f / (float) (Math.sqrt(dj) + 1e-3);
        sb.set(i, a, wi, b2, wj);
    }

    private DreamerRig() {
    }
}
