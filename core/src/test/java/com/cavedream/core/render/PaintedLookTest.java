package com.cavedream.core.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 捏脸上色数据测试：全黑底+默认人、纯黑=透明键、橡皮擦成黑、包围盒、按部位填色、吸附调色板。 */
class PaintedLookTest {

    @Test
    void canvasStartsBlackWithHumanAndBlackIsKey() {
        PaintedLook l = new PaintedLook();
        assertThat(l.colors[0]).isEqualTo(PaintedLook.KEY);   // 左上体外 → 黑
        assertThat(l.opaque(0, 0)).isFalse();                // 黑=不可见
        l.paint(0, 0, 0xFF33CC66);
        assertThat(l.opaque(0, 0)).isTrue();                 // 涂色后成为像素
        l.erase(0, 0);
        assertThat(l.colors[0]).isEqualTo(PaintedLook.KEY);  // 橡皮擦成黑
    }

    @Test
    void paintsSnapToPaletteAnywhere() {
        PaintedLook l = new PaintedLook();
        l.paint(3, 4, 0x123456);
        assertThat(l.colors[4 * PaintedLook.W + 3]).isEqualTo(Palette.snap(0x123456));
    }

    @Test
    void boundsEnclosesBodyAndNullWhenAllBlack() {
        PaintedLook l = new PaintedLook();
        int[] b = l.bounds();
        assertThat(b).isNotNull();
        assertThat(b[2]).isPositive();
        assertThat(b[3]).isPositive();
        for (int y = 0; y < PaintedLook.H; y++) {
            for (int x = 0; x < PaintedLook.W; x++) {
                l.erase(x, y);
            }
        }
        assertThat(l.bounds()).isNull();                     // 全黑→无实体
    }

    @Test
    void fillRegionPaintsAllRegionCells() {
        PaintedLook l = new PaintedLook();
        l.fillRegion(PaintedLook.R_HAIR, 0xFF00FF00);
        byte[] r = l.regions();
        int expect = Palette.snap(0xFF00FF00);
        boolean any = false;
        for (int i = 0; i < r.length; i++) {
            if (r[i] == PaintedLook.R_HAIR) {
                any = true;
                assertThat(l.colors[i]).isEqualTo(expect);
            }
        }
        assertThat(any).isTrue();
    }
}
