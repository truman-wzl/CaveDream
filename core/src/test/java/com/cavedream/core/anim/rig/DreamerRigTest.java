package com.cavedream.core.anim.rig;

import com.cavedream.core.anim.BoneId;
import com.cavedream.core.anim.Skeleton;
import com.cavedream.core.render.PaintedLook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 梦之主骨架与自动蒙皮（纯逻辑）：bind 世界=枢轴、层级合法、逐格权重归一、就近主骨正确。 */
class DreamerRigTest {

    @Test
    void bindWorldEqualsPivots() {
        Skeleton sk = DreamerRig.newSkeleton();
        sk.computeBindWorld();
        for (BoneId b : BoneId.values()) {
            assertThat(sk.world(b).e).as(b + ".x 应等于枢轴 x")
                    .isCloseTo(DreamerRig.PX[b.index()], within(1e-3f));
            assertThat(sk.world(b).f).as(b + ".y 应等于枢轴 y")
                    .isCloseTo(DreamerRig.PY[b.index()], within(1e-3f));
            assertThat(sk.world(b).a).isCloseTo(1f, within(1e-4f));   // bind 无旋转/缩放
        }
    }

    @Test
    void parentAlwaysLowerOrdinal() {
        Skeleton sk = DreamerRig.newSkeleton();
        for (BoneId b : BoneId.values()) {
            if (b == BoneId.ROOT) {
                assertThat(sk.parent(b)).isEqualTo(-1);
            } else {
                assertThat(sk.parent(b)).as(b + " 需有父且父序号在前").isGreaterThanOrEqualTo(0);
                assertThat(sk.parent(b)).isLessThan(b.index());
            }
        }
    }

    @Test
    void bindInvariants() {
        byte[] regions = PaintedLook.regions(0);   // 默认梦之主部位遮罩
        SkinBinding sb = DreamerRig.bind(new PaintedLook(0));
        assertThat(sb.boundCount()).as("应有大量像素被绑定").isGreaterThan(150);
        for (int i = 0; i < sb.bound.length; i++) {
            if (!sb.bound[i]) {
                continue;
            }
            assertThat(sb.wA[i] + sb.wB[i]).as("权重和=1 i=" + i).isCloseTo(1f, within(1e-4f));
            assertThat(sb.wA[i]).as("主骨权重≥副骨 i=" + i).isGreaterThanOrEqualTo(sb.wB[i]);
            assertThat(sb.wB[i]).isGreaterThanOrEqualTo(0f);
            assertThat(sb.boneA[i] == sb.boneB[i] || sb.wB[i] > 0f).as("副骨仅在有权重时出现").isTrue();
        }
    }

    @Test
    void scalpBindsToHeadAndFootToFrontFoot() {
        byte[] regions = PaintedLook.regions(0);
        SkinBinding sb = DreamerRig.bind(new PaintedLook(0));
        int scalp = 6 * DreamerRig.W + 10;            // 头顶中央（y=6 在头圆内）
        assertThat(sb.bound[scalp]).isTrue();
        assertThat(sb.boneA(scalp)).isEqualTo(BoneId.HEAD);
        assertThat(sb.wA[scalp]).isGreaterThan(0.5f);

        int toe = 39 * DreamerRig.W + 11;             // 前脚（x=11≥8.5, y=39）
        assertThat(sb.bound[toe]).isTrue();
        assertThat(sb.boneA(toe)).isEqualTo(BoneId.FOOT_F);
    }
}
