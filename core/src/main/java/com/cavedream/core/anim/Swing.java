package com.cavedream.core.anim;

/**
 * 挥击基础动作（OOP：所有手持物共用这一条弧，未来不同武器可派生不同 start/end/时长）。
 * 纯逻辑、可单测。基础动作 = 头顶起手 → 身前下方劈砍（递减角），easeOut 加速下劈。
 * 角度约定：rot=0 武器尖端朝上，正为逆时针；面向 facing<0 时镜像取反。
 */
public final class Swing {

    private final float startDeg;
    private final float endDeg;
    private final float duration;

    private float t;
    private boolean active;

    public Swing(float startDeg, float endDeg, float duration) {
        this.startDeg = startDeg;
        this.endDeg = endDeg;
        this.duration = Math.max(0.05f, duration);
    }

    /** 基础劈砍：头顶(30°)→身前下方(-150°)，0.28 秒。 */
    public static Swing chop() {
        return new Swing(30f, -150f, 0.28f);
    }

    public void restart() {
        t = 0f;
        active = true;
    }

    public void update(float dt) {
        if (!active) {
            return;
        }
        t += dt / duration;
        if (t >= 1f) {
            t = 1f;
            active = false;
        }
    }

    public boolean isActive() {
        return active;
    }

    /** 归一化进度 0~1（供“命中帧”同步伤害判定）。 */
    public float progress() {
        return t;
    }

    /** 当前武器角度：静止返回 restDeg，否则沿 start→end 走 easeOut；facing<0 镜像。 */
    public float angle(float restDeg, int facing) {
        float a = active ? startDeg + (endDeg - startDeg) * easeOut(t) : restDeg;
        return facing > 0 ? a : -a;
    }

    private static float easeOut(float x) {
        return 1f - (1f - x) * (1f - x);
    }
}
