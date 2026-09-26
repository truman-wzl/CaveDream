package com.cavedream.core.anim.rig;

import com.cavedream.core.anim.BoneId;
import com.cavedream.core.anim.Skeleton;
import com.cavedream.core.appearance.PixelLookForge;
import com.cavedream.core.render.PaintedLook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** 人形骨架与自动蒙皮（纯逻辑）：bind 世界=枢轴、层级合法、逐格权重归一、就近主骨正确。 */
class DreamerRigTest {

    private static PaintedLook look() {
        return PixelLookForge.defaultLook();
    }

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
        SkinBinding sb = DreamerRig.bind(look());
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
        SkinBinding sb = DreamerRig.bind(look());
        int scalp = (int) (0.10 * DreamerRig.H) * DreamerRig.W + DreamerRig.W / 2;   // 头顶中央
        assertThat(sb.bound[scalp]).as("头顶应被绑定").isTrue();
        assertThat(sb.boneA(scalp)).isEqualTo(BoneId.HEAD);
        assertThat(sb.wA[scalp]).isGreaterThan(0.5f);

        int toe = (int) (0.93 * DreamerRig.H) * DreamerRig.W + (int) (0.55 * DreamerRig.W);   // 前脚
        assertThat(sb.bound[toe]).as("前脚应被绑定").isTrue();
        assertThat(sb.boneA(toe)).isEqualTo(BoneId.FOOT_F);
    }
}
