package com.cavedream.core.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** L1 箱庭生成器测试：三段式结构、雾边界、出生点安全、种子确定性、资源存在。 */
class LayerGeneratorTest {

    @Test
    void sameSeedShouldGenerateIdenticalWorld() {
        LayerWorld a = LayerGenerator.shallowGarden(99L, 64, 48);
        LayerWorld b = LayerGenerator.shallowGarden(99L, 64, 48);
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 48; y++) {
                assertThat(b.blockAt(x, y)).isEqualTo(a.blockAt(x, y));
            }
        }
    }

    @Test
    void fogShouldSealEntireBorder() {
        LayerWorld world = LayerGenerator.shallowGarden(1L, 64, 48);
        for (int x = 0; x < world.getWidth(); x++) {
            assertThat(world.blockAt(x, 0)).isEqualTo(BlockType.FOG);
            assertThat(world.blockAt(x, world.getHeight() - 1)).isEqualTo(BlockType.FOG);
        }
        for (int y = 0; y < world.getHeight(); y++) {
            assertThat(world.blockAt(0, y)).isEqualTo(BlockType.FOG);
            assertThat(world.blockAt(world.getWidth() - 1, y)).isEqualTo(BlockType.FOG);
        }
    }

    @Test
    void spawnShouldBeAirWithSolidGroundBelow() {
        LayerWorld world = LayerGenerator.shallowGarden(7L, 64, 48);
        int sx = world.getSpawnTileX();
        int sy = world.getSpawnTileY();

        assertThat(world.blockAt(sx, sy)).isEqualTo(BlockType.AIR);
        assertThat(world.isSolid(sx, sy - 1)).isTrue();     // 脚下有地
        assertThat(world.blockAt(sx, sy + 1)).isEqualTo(BlockType.AIR); // 头顶开阔
    }

    @Test
    void gardenShouldContainVerticalZonesAndResources() {
        LayerWorld world = LayerGenerator.shallowGarden(20260922L);
        assertThat(world.getWidth()).isEqualTo(LayerGenerator.L1_WIDTH);
        assertThat(world.getHeight()).isEqualTo(LayerGenerator.L1_HEIGHT);

        int grass = 0;
        int stone = 0;
        int ore = 0;
        int wood = 0;
        for (int x = 0; x < world.getWidth(); x++) {
            for (int y = 0; y < world.getHeight(); y++) {
                switch (world.blockAt(x, y)) {
                    case GRASS -> grass++;
                    case STONE -> stone++;
                    case DEEP_SEED -> ore++;
                    case WOOD -> wood++;
                    default -> { }
                }
            }
        }
        assertThat(grass).as("生态层地表").isGreaterThan(100);
        assertThat(stone).as("深层岩石").isGreaterThan(5000);
        assertThat(ore).as("沉眠矿应可采到").isGreaterThan(50);
        assertThat(wood).as("树应该存在").isGreaterThan(10);
    }
}
