package com.cavedream.core.world;

import com.cavedream.core.item.Item;
import com.cavedream.core.light.LightEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 树对象化的纯逻辑不变量（不依赖 libGDX/GL）：非实体、可被斧砍、注册为可放置物品、
 * 且作为“非实心”不阻断天光（泰拉瑞亚式透光）。
 */
class TreeTest {

    @Test
    void treeIsNonSolidSpecialObject() {
        BlockType t = BlockType.TREE;
        assertThat(t.solid()).as("树非实体：不挡路").isFalse();
        assertThat(t.isTree()).isTrue();
        assertThat(t.backgroundWall()).isFalse();
        assertThat(t.platform()).isFalse();
        assertThat(t.liquid()).isFalse();
        assertThat(t.wallMountable()).isFalse();
        assertThat(t.mineable()).as("树可被砍（斧）").isTrue();
    }

    @Test
    void onlyTreeReportsIsTree() {
        assertThat(BlockType.WOOD.isTree()).isFalse();
        assertThat(BlockType.LEAF.isTree()).isFalse();
        assertThat(BlockType.AIR.isTree()).isFalse();
    }

    @Test
    void treeSizeConstants() {
        assertThat(BlockType.TREE_W).isEqualTo(3);
        assertThat(BlockType.TREE_MIN_H).isEqualTo(6);
        assertThat(BlockType.TREE_MAX_H).isEqualTo(16);
        assertThat(BlockType.TREE_START_H).isLessThanOrEqualTo(BlockType.TREE_MIN_H);
    }

    @Test
    void treeItemIsAutoRegisteredPlaceable() {
        Item it = Item.ofBlock(BlockType.TREE);
        assertThat(it).isNotNull();
        assertThat(it.id()).isEqualTo(27);
        assertThat(it.placeable()).isTrue();
        assertThat(it.block()).isEqualTo(BlockType.TREE);
    }

    @Test
    void treeDoesNotBlockSky() {
        // 一堵横贯的树墙（非实心）应让天光透到其下方；对照：换成实心石则下方变暗。
        LayerWorld leafy = new LayerWorld(12, 20);
        for (int x = 0; x < 12; x++) {
            leafy.setBlock(x, 10, BlockType.TREE);
        }
        LightEngine e1 = new LightEngine();
        e1.recompute(leafy);
        assertThat(e1.skyAt(6, 4)).as("天光穿过非实体树抵达其下方").isEqualTo(LightEngine.MAX_LEVEL);

        LayerWorld stony = new LayerWorld(12, 20);
        for (int x = 0; x < 12; x++) {
            stony.setBlock(x, 10, BlockType.STONE);
        }
        LightEngine e2 = new LightEngine();
        e2.recompute(stony);
        assertThat(e2.skyAt(6, 4)).as("实心石削弱其正下方天光").isLessThan(LightEngine.MAX_LEVEL);
    }
}
