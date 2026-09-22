package com.cavedream.core.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 箱庭世界数据测试：坐标约定、越界安全网、读写。 */
class LayerWorldTest {

    @Test
    void shouldStoreAndReadBlocks() {
        LayerWorld world = new LayerWorld(10, 10);
        assertThat(world.blockAt(3, 4)).isEqualTo(BlockType.AIR);

        world.setBlock(3, 4, BlockType.STONE);
        assertThat(world.blockAt(3, 4)).isEqualTo(BlockType.STONE);
        assertThat(world.isSolid(3, 4)).isTrue();
        assertThat(world.isSolid(3, 5)).isFalse();
    }

    @Test
    void outOfBoundsShouldBehaveAsFogWall() {
        LayerWorld world = new LayerWorld(10, 10);
        assertThat(world.inBounds(-1, 5)).isFalse();
        assertThat(world.blockAt(-1, 5)).isEqualTo(BlockType.FOG);
        assertThat(world.blockAt(100, 100)).isEqualTo(BlockType.FOG);
        assertThat(world.isSolid(999, -3)).isTrue();   // 物理安全网：外面永远实心
    }

    @Test
    void setBlockOutsideBoundsShouldThrow() {
        LayerWorld world = new LayerWorld(8, 8);
        assertThatThrownBy(() -> world.setBlock(8, 0, BlockType.DIRT))
                .isInstanceOf(IndexOutOfBoundsException.class);
    }
}
