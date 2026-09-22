package com.cavedream.core.world;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 玩家物理测试：重力落地、墙阻挡、跳跃、放置占用检测。 */
class PlayerEntityTest {

    /** 平地世界：y=0..9 全石，上方空气。 */
    private static LayerWorld flatWorld() {
        LayerWorld world = new LayerWorld(32, 32);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 10; y++) {
                world.setBlock(x, y, BlockType.STONE);
            }
        }
        return world;
    }

    @Test
    void shouldFallAndLandOnGround() {
        LayerWorld world = flatWorld();
        PlayerEntity player = new PlayerEntity(100f, 300f);

        for (int i = 0; i < 120; i++) {
            player.update(world, false, false, false, 1 / 60f);
        }

        assertThat(player.isOnGround()).isTrue();
        // 地面顶在 y = 10*16 = 160 处
        assertThat(player.y()).isCloseTo(160f, org.assertj.core.data.Offset.offset(0.5f));
    }

    @Test
    void shouldNotWalkThroughWall() {
        LayerWorld world = flatWorld();
        for (int y = 10; y < 20; y++) {
            world.setBlock(15, y, BlockType.STONE);   // 一堵墙
        }
        PlayerEntity player = new PlayerEntity(100f, 161f);

        for (int i = 0; i < 240; i++) {
            player.update(world, false, true, false, 1 / 60f);
        }

        // 应被墙（x=15 → 像素 240）挡住
        assertThat(player.x() + player.width()).isLessThanOrEqualTo(240f + 0.1f);
    }

    @Test
    void jumpShouldRiseThenComeBack() {
        LayerWorld world = flatWorld();
        PlayerEntity player = new PlayerEntity(100f, 161f);
        assertThat(player.isOnGround()).isFalse();
        player.update(world, false, false, false, 1 / 60f);   // 先落定
        for (int i = 0; i < 30 && !player.isOnGround(); i++) {
            player.update(world, false, false, false, 1 / 60f);
        }
        float standY = player.y();

        player.update(world, false, false, true, 1 / 60f);
        float peak = standY;
        for (int i = 0; i < 120; i++) {
            player.update(world, false, false, false, 1 / 60f);
            peak = Math.max(peak, player.y());
        }

        assertThat(peak - standY).as("应有明显跳起高度").isGreaterThan(40f);
        assertThat(player.y()).as("最终落回原地").isCloseTo(standY, org.assertj.core.data.Offset.offset(1f));
    }

    @Test
    void overlapsTileShouldDetectPlacementConflict() {
        PlayerEntity player = new PlayerEntity(100f, 160f);   // 占 tile (6..7, 10..11)
        assertThat(player.overlapsTile(6, 10)).isTrue();
        assertThat(player.overlapsTile(20, 20)).isFalse();
    }
}
