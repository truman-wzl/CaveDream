package com.cavedream.core.anim;

/**
 * 动画器：把剪辑求值成一个最终姿态并写入骨架。当前实现两层——
 *  1) 移动基础层：idle↔walk↔run 依归一化速度 {@code speedNorm}（0=停,1=跑）连续混合；
 *  2) 叠加层：一次性 overlay（跳跃/落地/攻击等），按骨遮罩叠加到基础层之上、播完自动卸。
 * 纯逻辑、可单测；渲染层读 {@link #skeleton()} 的世界变换做蒙皮。
 */
public final class Animator {

    private final Skeleton skeleton;
    private final Pose current = new Pose();
    private final Pose scratch = new Pose();

    private PoseClip idle, walk, run;
    private float time;

    // overlay 状态
    private PoseClip overlay;
    private boolean[] overlayMask;
    private float overlayWeight = 1f;
    private float overlayTime;
    private boolean overlayLoop;

    public Animator(Skeleton skeleton) {
        this.skeleton = skeleton;
    }

    public Skeleton skeleton() {
        return skeleton;
    }

    public Pose pose() {
        return current;
    }

    public void setLocomotion(PoseClip idle, PoseClip walk, PoseClip run) {
        this.idle = idle;
        this.walk = walk;
        this.run = run;
    }

    /** 触发一次性叠加动作；mask 为空表示全身。 */
    public void playOverlay(PoseClip clip, boolean[] mask, float weight, boolean loop) {
        this.overlay = clip;
        this.overlayMask = mask;
        this.overlayWeight = weight;
        this.overlayLoop = loop;
        this.overlayTime = 0f;
    }

    public boolean isOverlayActive() {
        return overlay != null;
    }

    /** 每帧：推进时间，求值移动层，叠加 overlay 层，算世界变换。 */
    public void update(float dt, float speedNorm) {
        time += dt;
        locomotion(speedNorm, current);
        if (overlay != null) {
            overlay.sample(overlayTime, scratch);
            current.overlay(scratch, overlayMask, overlayWeight);
            overlayTime += dt;
            if (!overlayLoop && overlayTime > overlay.duration) {
                overlay = null;
            }
        }
        skeleton.computeWorld(current);
    }

    /** idle↔walk↔run 连续混合：0~0.5 由 idle→walk、0.5~1 由 walk→run（各自按 time 采样循环）。 */
    private void locomotion(float speedNorm, Pose out) {
        float s = speedNorm < 0f ? 0f : (speedNorm > 1f ? 1f : speedNorm);
        if (walk == null) {
            if (idle != null) {
                idle.sample(time, out);
            } else {
                out.reset();
            }
            return;
        }
        if (s <= 0.5f) {
            float k = s / 0.5f;
            Pose a = scratchA(), b = scratchB();
            if (idle != null) {
                idle.sample(time, a);
            } else {
                a.reset();
            }
            walk.sample(time, b);
            out.blend(a, b, k);
        } else {
            float k = (s - 0.5f) / 0.5f;
            Pose a = scratchA(), b = scratchB();
            walk.sample(time, a);
            if (run != null) {
                run.sample(time, b);
            } else {
                b.copyFrom(a);
            }
            out.blend(a, b, k);
        }
    }

    // 复用两个临时 Pose（locomotion 期间不重入）
    private final Pose tmpA = new Pose();
    private final Pose tmpB = new Pose();

    private Pose scratchA() {
        return tmpA;
    }

    private Pose scratchB() {
        return tmpB;
    }
}
