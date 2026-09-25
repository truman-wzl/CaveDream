package com.cavedream.core.anim;

/**
 * 缓动曲线库：输入归一化进度 x∈[0,1]，输出同域进度（outBack/outElastic 可越界以产生过冲/回弹）。
 * 纯静态、可单测。动画"软/弹/顿"的手感主要来自这里的曲线选择。
 */
public final class Easing {

    private Easing() {
    }

    public static float linear(float x) {
        return x;
    }

    public static float inQuad(float x) {
        return x * x;
    }

    public static float outQuad(float x) {
        return 1f - (1f - x) * (1f - x);
    }

    public static float inOutQuad(float x) {
        return x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
    }

    public static float inOutCubic(float x) {
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    }

    /** 正弦缓动：首尾柔和，常用于呼吸/待机。 */
    public static float inOutSine(float x) {
        return (float) (-(Math.cos(Math.PI * x) - 1) / 2);
    }

    /** 后退式过冲（overshoot）：末段冲过再回位，制造弹性。 */
    public static float outBack(float x) {
        final float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(x - 1, 3) + c1 * (float) Math.pow(x - 1, 2);
    }

    /** 衰减弹跳（elastic），落地/急停用；x=0→0，x=1→1。 */
    public static float outElastic(float x) {
        final float c4 = (float) (2 * Math.PI / 3);
        if (x <= 0f) {
            return 0f;
        }
        if (x >= 1f) {
            return 1f;
        }
        return (float) (Math.pow(2, -10 * x) * Math.sin((x * 10f - 0.75f) * c4) + 1);
    }

    /** 按枚举取曲线。 */
    public static float apply(Curve c, float x) {
        float t = x < 0f ? 0f : (x > 1f ? 1f : x);
        switch (c) {
            case IN_QUAD: return inQuad(t);
            case OUT_QUAD: return outQuad(t);
            case IN_OUT_QUAD: return inOutQuad(t);
            case IN_OUT_CUBIC: return inOutCubic(t);
            case IN_OUT_SINE: return inOutSine(t);
            case OUT_BACK: return outBack(t);
            case OUT_ELASTIC: return outElastic(t);
            case LINEAR:
            default: return linear(t);
        }
    }

    public enum Curve {
        LINEAR, IN_QUAD, OUT_QUAD, IN_OUT_QUAD, IN_OUT_CUBIC, IN_OUT_SINE, OUT_BACK, OUT_ELASTIC
    }
}
