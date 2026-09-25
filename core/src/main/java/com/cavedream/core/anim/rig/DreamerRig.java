package com.cavedream.core.anim.rig;

import com.cavedream.core.anim.BoneId;
import com.cavedream.core.anim.Skeleton;
import com.cavedream.core.anim.Transform2;
import com.cavedream.core.render.PaintedLook;

/**
 * 梦之主骨架与自动蒙皮（像素坐标 y 向下，0=头顶，42=脚底；21×42 网格）。
 * 绑定采用通用的"就近双骨 + 逆距权重"：取该格所属肢体链中离它最近的两根骨，按距离倒数分配权重
 * → 关节处自然柔化过渡。此法与实体无关，未来怪物/武器复用同一思路，只换枢轴与候选链。
 */
public final class DreamerRig {

    public static final int W = 21, H = 42;

    // 骨枢轴（像素，y 向下）
    static final float[] PX = new float[BoneId.COUNT];
    static final float[] PY = new float[BoneId.COUNT];
    // 父子（-1=根）；父骨序号恒小于子骨，保证 Skeleton 顺序遍历可先算父
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

        pivot(BoneId.ROOT, 10, 41);
        pivot(BoneId.HIPS, 10, 29);
        pivot(BoneId.SPINE, 10, 22);
        pivot(BoneId.HEAD, 10, 10);
        pivot(BoneId.HAIR, 4, 14);
        pivot(BoneId.ARM_UB, 2.5f, 19);
        pivot(BoneId.FORE_UB, 2.5f, 23);
        pivot(BoneId.HAND_UB, 2.5f, 26);
        pivot(BoneId.ARM_FB, 17.5f, 19);
        pivot(BoneId.FORE_FB, 17.5f, 23);
        pivot(BoneId.HAND_FB, 17.5f, 26);
        pivot(BoneId.THIGH_B, 7, 29);
        pivot(BoneId.SHIN_B, 7, 34);
        pivot(BoneId.FOOT_B, 7, 40);
        pivot(BoneId.THIGH_F, 13, 29);
        pivot(BoneId.SHIN_F, 13, 34);
        pivot(BoneId.FOOT_F, 13.5f, 40);
    }

    private static void parent(BoneId c, BoneId p) {
        PARENT[c.index()] = p == null ? -1 : p.index();
    }

    private static void pivot(BoneId b, float x, float y) {
        PX[b.index()] = x;
        PY[b.index()] = y;
    }

    /** 构造梦之主骨架：bind 局部变换=相对父骨的平移（无旋转），故 bind 世界变换的平移即骨枢轴。 */
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

    /** 由上色调色生成逐格蒙皮绑定。 */
    public static SkinBinding bind(PaintedLook look) {
        return bind(look.regions(), look.colors);
    }

    /**
     * 依 region + 颜色生成逐格绑定：不透明但 region==0 的描边像素按邻域 region 膨胀归类，
     * 再按 {@link #candidates} 逐骨就近双骨赋权。
     */
    public static SkinBinding bind(byte[] regions, int[] colors) {
        SkinBinding sb = new SkinBinding(W, H);
        byte[] eff = new byte[W * H];
        System.arraycopy(regions, 0, eff, 0, Math.min(regions.length, eff.length));
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                boolean opaque = i < colors.length && (colors[i] & 0xFFFFFF) != 0;
                if (opaque && eff[i] == 0) {
                    eff[i] = neighborRegion(regions, x, y);   // 描边借邻域归类
                }
            }
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                int i = y * W + x;
                if (i >= colors.length || (colors[i] & 0xFFFFFF) == 0 || eff[i] == 0) {
                    continue;
                }
                bindNearest2(sb, i, candidates(x, y, eff[i]));
            }
        }
        return sb;
    }

    private static byte neighborRegion(byte[] regions, int x, int y) {
        int[][] d = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (int[] k : d) {
            int nx = x + k[0], ny = y + k[1];
            if (nx >= 0 && ny >= 0 && nx < W && ny < H && ny * W + nx < regions.length && regions[ny * W + nx] != 0) {
                return regions[ny * W + nx];
            }
        }
        return 0;
    }

    /** 按 region + 左右/上下归到对应肢体骨链（front = x≥中心10）。 */
    private static BoneId[] candidates(int x, int y, byte reg) {
        boolean front = x >= 10;
        switch (reg) {
            case 1:   // 头发
                return x <= 6 ? arr(BoneId.HAIR, BoneId.HEAD) : arr(BoneId.HEAD, BoneId.HAIR);
            case 2: case 3: case 4:   // 肤/眼/腮 → 头/颈
                return y < 17 ? arr(BoneId.HEAD, BoneId.SPINE) : arr(BoneId.SPINE, BoneId.HEAD);
            case 5:   // 衣袍 → 躯干
                return arr(BoneId.SPINE, BoneId.HIPS);
            case 6:   // 手臂
                if (y < 21) {
                    return front ? arr(BoneId.ARM_FB, BoneId.FORE_FB) : arr(BoneId.ARM_UB, BoneId.FORE_UB);
                }
                if (y < 24) {
                    return front ? arr(BoneId.FORE_FB, BoneId.HAND_FB) : arr(BoneId.FORE_UB, BoneId.HAND_UB);
                }
                return front ? arr(BoneId.HAND_FB, BoneId.FORE_FB) : arr(BoneId.HAND_UB, BoneId.FORE_UB);
            case 7:   // 裤 → 大腿/小腿
                if (y < 33) {
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
    private static void bindNearest2(SkinBinding sb, int i, BoneId[] cand) {
        int n = cand.length;
        if (n == 1) {
            sb.set(i, cand[0], 1f, cand[0], 0f);
            return;
        }
        int x = i % W, y = i / W;
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
