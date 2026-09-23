package com.cavedream.core.render;

import com.cavedream.core.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 烘焙像素契约测试：确保程序化 ARGB 缓冲尺寸/透明度正确，作为 DB 种子 PNG 的来源。 */
class BlockTexturesBakeTest {

    @Test
    void sheetIsOpaqueAndSized() {
        int[] argb = BlockTextures.bakeSheetArgb(BlockType.DIRT);
        assertThat(argb).hasSize(BlockTextures.SHEET_SIZE * BlockTextures.SHEET_SIZE);
        for (int p : argb) {
            assertThat((p >>> 24) & 0xFF).isEqualTo(0xFF);   // 全不透明
        }
    }

    @Test
    void differentMaterialsDiffer() {
        int[] dirt = BlockTextures.bakeSheetArgb(BlockType.DIRT);
        int[] stone = BlockTextures.bakeSheetArgb(BlockType.STONE);
        assertThat(dirt).isNotEqualTo(stone);
    }

    @Test
    void lipHasTransparentTop() {
        int[] lip = BlockTextures.bakeLipArgb();
        assertThat(lip).hasSize(BlockTextures.LIP_W * BlockTextures.LIP_H);
        // 底部应有不透明草沿，某些行存在透明像素（起伏）
        boolean anyOpaque = false, anyTransparent = false;
        for (int p : lip) {
            if (((p >>> 24) & 0xFF) == 0) {
                anyTransparent = true;
            } else {
                anyOpaque = true;
            }
        }
        assertThat(anyOpaque).isTrue();
        assertThat(anyTransparent).isTrue();
    }
}
