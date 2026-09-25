package com.cavedream.core.anim;

import java.util.ArrayList;
import java.util.List;

/**
 * 姿态剪辑：一串带时间的关键帧（每帧存一份 {@link Pose} 快照 + 到下一帧的缓动曲线），
 * 按时间采样得到插值姿态。数据驱动（将来可由 JSON / LLM 编舞产出，一切皆 clipId）。纯逻辑、可单测。
 */
public final class PoseClip {

    public final int id;
    public final String name;
    public final float duration;
    public final boolean loop;
    private final List<Key> keys = new ArrayList<>();

    public PoseClip(int id, String name, float duration, boolean loop) {
        this.id = id;
        this.name = name;
        this.duration = Math.max(0.0001f, duration);
        this.loop = loop;
    }

    /** 追加关键帧（自动快照 pose，避免外部复用同一 Pose 对象被后续改写）。 */
    public PoseClip key(float time, Pose pose, Easing.Curve curveToNext) {
        Key k = new Key();
        k.time = time;
        k.pose = new Pose();
        k.pose.copyFrom(pose);
        k.curve = curveToNext;
        keys.add(k);
        return this;
    }

    public int keyCount() {
        return keys.size();
    }

    /** 采样 time→out 姿态。无关键帧则 out 复位为恒等。 */
    public void sample(float time, Pose out) {
        if (keys.isEmpty()) {
            out.reset();
            return;
        }
        if (keys.size() == 1) {
            out.copyFrom(keys.get(0).pose);
            return;
        }
        float t = time;
        if (loop) {
            t = t % duration;
            if (t < 0f) {
                t += duration;
            }
        } else if (t < 0f) {
            t = 0f;
        } else if (t > duration) {
            t = duration;
        }
        int i = 0;
        while (i < keys.size() - 1 && keys.get(i + 1).time <= t) {
            i++;
        }
        if (i >= keys.size() - 1) {
            out.copyFrom(keys.get(keys.size() - 1).pose);
            return;
        }
        Key a = keys.get(i), b = keys.get(i + 1);
        float span = b.time - a.time;
        float x = span <= 0f ? 1f : (t - a.time) / span;
        out.blend(a.pose, b.pose, Easing.apply(a.curve, x));
    }

    private static final class Key {
        float time;
        Pose pose;
        Easing.Curve curve;
    }
}
