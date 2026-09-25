package com.cavedream.core.anim;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 动画引擎纯逻辑测试：变换/骨骼层级/姿态混合/缓动/剪辑采样/动画器分层/状态机。不依赖 GL。 */
class AnimEngineTest {

    private static Transform2[] allIdentity() {
        Transform2[] t = new Transform2[BoneId.COUNT];
        for (int i = 0; i < BoneId.COUNT; i++) {
            t[i] = new Transform2();
        }
        return t;
    }

    @Test
    void transformRotate90MapsUnitXToUnitY() {
        Transform2 m = new Transform2();
        m.setTrs(0, 0, 90, 1, 1);
        assertThat(m.ax(1, 0)).isCloseTo(0f, within(1e-4f));
        assertThat(m.ay(1, 0)).isCloseTo(1f, within(1e-4f));
    }

    @Test
    void transformMulComposesParentThenChild() {
        Transform2 parent = new Transform2();
        parent.setTrs(10, 0, 0, 1, 1);          // 父：平移 x+10
        Transform2 child = new Transform2();
        child.setTrs(0, 5, 0, 1, 1);            // 子：平移 y+5
        parent.mul(child);                       // world = parent * child
        assertThat(parent.ax(0, 0)).isCloseTo(10f, within(1e-4f));
        assertThat(parent.ay(0, 0)).isCloseTo(5f, within(1e-4f));
    }

    @Test
    void poseBlendLerpAndOverlayMask() {
        Pose a = new Pose();
        a.setBone(BoneId.HEAD, 0, 0, 0, 1, 1);
        Pose b = new Pose();
        b.setBone(BoneId.HEAD, 20, 0, 0, 1, 1);
        Pose out = new Pose();
        out.blend(a, b, 0.25f);
        assertThat(out.rot[BoneId.HEAD.index()]).isCloseTo(5f, within(1e-4f));

        // overlay：仅命中骨被覆盖，其余保持基础层
        Pose base = new Pose();
        base.setBone(BoneId.SPINE, 3, 0, 0, 1, 1);
        base.setBone(BoneId.HEAD, 4, 0, 0, 1, 1);
        Pose ov = new Pose();
        ov.setBone(BoneId.HEAD, 40, 0, 0, 1, 1);
        boolean[] mask = new boolean[BoneId.COUNT];
        mask[BoneId.HEAD.index()] = true;
        base.overlay(ov, mask, 1f);
        assertThat(base.rot[BoneId.HEAD.index()]).isCloseTo(40f, within(1e-4f));
        assertThat(base.rot[BoneId.SPINE.index()]).isCloseTo(3f, within(1e-4f));
    }

    @Test
    void easingEndpointsAndOvershoot() {
        assertThat(Easing.inOutCubic(0f)).isCloseTo(0f, within(1e-4f));
        assertThat(Easing.inOutCubic(1f)).isCloseTo(1f, within(1e-4f));
        // outBack 末段应冲过 1（过冲）
        assertThat(Easing.outBack(0.7f)).isGreaterThan(1f);
        // apply 对入参越界钳制到 [0,1]
        assertThat(Easing.apply(Easing.Curve.LINEAR, -1f)).isCloseTo(0f, within(1e-6f));
        assertThat(Easing.apply(Easing.Curve.LINEAR, 2f)).isCloseTo(1f, within(1e-6f));
    }

    @Test
    void poseClipSamplesFirstLastMiddle() {
        Pose p0 = new Pose();
        p0.setBone(BoneId.HEAD, 0, 0, 0, 1, 1);
        Pose p1 = new Pose();
        p1.setBone(BoneId.HEAD, 10, 0, 0, 1, 1);
        PoseClip clip = new PoseClip(1, "t", 2f, false)
                .key(0f, p0, Easing.Curve.LINEAR)
                .key(2f, p1, Easing.Curve.LINEAR);
        Pose out = new Pose();
        clip.sample(0f, out);
        assertThat(out.rot[BoneId.HEAD.index()]).isCloseTo(0f, within(1e-4f));
        clip.sample(1f, out);
        assertThat(out.rot[BoneId.HEAD.index()]).isCloseTo(5f, within(1e-4f));
        clip.sample(2f, out);
        assertThat(out.rot[BoneId.HEAD.index()]).isCloseTo(10f, within(1e-4f));
    }

    private static PoseClip constClip(BoneId bone, float rot) {
        Pose p = new Pose();
        p.setBone(bone, rot, 0, 0, 1, 1);
        return new PoseClip(0, bone.name(), 1f, true).key(0f, p, Easing.Curve.LINEAR);
    }

    @Test
    void animatorLocomotionBlendsBySpeed() {
        Skeleton skel = new Skeleton(defaultParents(), allIdentity());
        Animator an = new Animator(skel);
        an.setLocomotion(constClip(BoneId.HEAD, 0), constClip(BoneId.HEAD, 10), constClip(BoneId.HEAD, 20));
        an.update(0.1f, 0f);
        assertThat(an.pose().rot[BoneId.HEAD.index()]).isCloseTo(0f, within(1e-4f));
        an.update(0.1f, 0.5f);
        assertThat(an.pose().rot[BoneId.HEAD.index()]).isCloseTo(10f, within(1e-4f));
        an.update(0.1f, 1f);
        assertThat(an.pose().rot[BoneId.HEAD.index()]).isCloseTo(20f, within(1e-4f));
    }

    @Test
    void animatorOverlayAppliesThenAutoClears() {
        Skeleton skel = new Skeleton(defaultParents(), allIdentity());
        Animator an = new Animator(skel);
        an.setLocomotion(constClip(BoneId.SPINE, 0), constClip(BoneId.SPINE, 0), constClip(BoneId.SPINE, 0));
        boolean[] mask = new boolean[BoneId.COUNT];
        mask[BoneId.SPINE.index()] = true;
        an.playOverlay(constClip(BoneId.SPINE, 30), mask, 1f, false);
        assertThat(an.isOverlayActive()).isTrue();
        an.update(0.01f, 0f);
        assertThat(an.pose().rot[BoneId.SPINE.index()]).isCloseTo(30f, within(1e-3f));
        an.update(2f, 0f);   // 超过 clip 时长 → 卸叠加
        assertThat(an.isOverlayActive()).isFalse();
        an.update(0.01f, 0f);
        assertThat(an.pose().rot[BoneId.SPINE.index()]).isCloseTo(0f, within(1e-3f));
    }

    @Test
    void stateMachineSpeedNorm() {
        StateMachine sm = new StateMachine();
        sm.update(false, true, true, 2f);
        assertThat(sm.state()).isEqualTo(AnimState.RUN);
        assertThat(sm.speedNorm()).isCloseTo(1f, within(1e-6f));
        sm.update(false, true, true, 1f);
        assertThat(sm.speedNorm()).isCloseTo(0.5f, within(1e-6f));
        sm.update(false, true, false, 0f);
        assertThat(sm.state()).isEqualTo(AnimState.IDLE);
        assertThat(sm.speedNorm()).isCloseTo(0f, within(1e-6f));
    }

    private static int[] defaultParents() {
        int[] p = new int[BoneId.COUNT];
        for (int i = 0; i < BoneId.COUNT; i++) {
            p[i] = 0;                        // 测试用扁平层级：除根外都挂 root
        }
        p[BoneId.ROOT.index()] = -1;
        return p;
    }
}
