package com.cavedream.core.anim;

/**
 * 骨架：骨父子层级 + bind 局部变换；给定 {@link Pose} 逐骨算出世界变换（父先于子，依 {@link BoneId} 顺序）。
 * 纯逻辑、可单测。蒙皮渲染读 {@link #world(BoneId)} 把像素绑到骨。
 */
public final class Skeleton {

    private final int[] parent;              // 每骨父索引，-1=根
    private final Transform2[] bindLocal;    // bind 姿态局部变换
    private final Transform2[] world;        // 计算缓存
    private final Transform2[] effLocal;     // bind ∘ pose 局部缓存
    private final Transform2 tmp = new Transform2();

    public Skeleton(int[] parents, Transform2[] bindLocal) {
        if (parents.length != BoneId.COUNT || bindLocal.length != BoneId.COUNT) {
            throw new IllegalArgumentException("骨架需 " + BoneId.COUNT + " 根骨");
        }
        this.parent = parents.clone();
        this.bindLocal = new Transform2[BoneId.COUNT];
        this.world = new Transform2[BoneId.COUNT];
        this.effLocal = new Transform2[BoneId.COUNT];
        for (int i = 0; i < BoneId.COUNT; i++) {
            this.bindLocal[i] = bindLocal[i].copy();
            this.world[i] = new Transform2();
            this.effLocal[i] = new Transform2();
        }
    }

    public int parent(BoneId bone) {
        return parent[bone.index()];
    }

    public Transform2 bindLocal(BoneId bone) {
        return bindLocal[bone.index()];
    }

    /** 由姿态算各骨世界变换（bone i：local = bind[i] * trs(pose[i])，world = world[parent] * local）。 */
    public void computeWorld(Pose pose) {
        for (int i = 0; i < BoneId.COUNT; i++) {
            tmp.setTrs(pose.tx[i], pose.ty[i], pose.rot[i], pose.sx[i], pose.sy[i]);
            effLocal[i].set(bindLocal[i]);
            effLocal[i].mul(tmp);                       // bind ∘ poseDelta
            int p = parent[i];
            if (p < 0) {
                world[i].set(effLocal[i]);
            } else {
                world[i].set(world[p]);
                world[i].mul(effLocal[i]);
            }
        }
    }

    public Transform2 world(BoneId bone) {
        return world[bone.index()];
    }

    /** bind 姿态（全恒等）下的世界变换：直接以恒等 Pose 计算。 */
    public void computeBindWorld() {
        computeWorld(BIND_POSE);
    }

    private static final Pose BIND_POSE = new Pose();   // 恒等
}
