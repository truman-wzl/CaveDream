package com.cavedream.core.anim;

/**
 * 状态机（纯逻辑、可单测）：由移动/疾跑/在地的输入派生 {@link AnimState} 与移动层混合强度
 * {@link #speedNorm()}（0=idle,0.5=walk,1=run），喂给 {@link Animator#update}。
 * 空中/水中状态先粗粒度映射（跳/落用 Animator overlay 触发，这里只给基础层强度）。
 */
public final class StateMachine {

    private AnimState state = AnimState.IDLE;

    /** @param speedRatio 当前水平速度 / 走路基准速度（≈1 走、≈2 跑） */
    public AnimState update(boolean inWater, boolean onGround, boolean moving, float speedRatio) {
        if (inWater) {
            state = AnimState.SWIM;
        } else if (!onGround) {
            state = AnimState.FALL;
        } else if (!moving) {
            state = AnimState.IDLE;
        } else {
            state = speedRatio >= 1.5f ? AnimState.RUN : AnimState.WALK;
        }
        return state;
    }

    public AnimState state() {
        return state;
    }

    /** 移动层混合强度：IDLE→0、WALK→0.5、RUN→1、空中/水中沿用当前地面强度语义。 */
    public float speedNorm() {
        switch (state) {
            case RUN: return 1f;
            case WALK: return 0.5f;
            case SWIM: return 0.4f;
            case IDLE:
            case FALL:
            default: return 0f;
        }
    }
}
