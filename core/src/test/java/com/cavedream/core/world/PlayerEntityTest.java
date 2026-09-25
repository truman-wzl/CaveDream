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

    /** 高世界：地面同高但上方留更多坠落空间（测封顶伤害）。 */
    private static LayerWorld tallWorld() {
        LayerWorld world = new LayerWorld(32, 80);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 10; y++) {
                world.setBlock(x, y, BlockType.STONE);
            }
        }
        return world;
    }

    @Test
    void shortFallShouldNotHurt() {
        LayerWorld world = flatWorld();
        PlayerEntity player = new PlayerEntity(100f, 220f);   // 落至 160（60px）→冲击速度低于安全阈值
        for (int i = 0; i < 200; i++) {
            player.update(world, false, false, false, 1 / 60f);
        }
        assertThat(player.isOnGround()).isTrue();
        assertThat(player.consumeFallDamage()).isZero();
    }

    @Test
    void jumpShouldNotCauseFallDamage() {
        LayerWorld world = flatWorld();
        PlayerEntity player = new PlayerEntity(100f, 161f);
        for (int i = 0; i < 30 && !player.isOnGround(); i++) {
            player.update(world, false, false, false, 1 / 60f);   // 先落定
        }
        player.setGravityScale(0.5f);   // 天空带低重力：跳得更高，但落地速度不变→仍不应摔伤
        player.update(world, false, false, true, 1 / 60f);        // 起跳
        boolean leftGround = false;
        for (int i = 0; i < 300; i++) {
            player.update(world, false, false, false, 1 / 60f);
            if (!player.isOnGround()) {
                leftGround = true;
            } else if (leftGround) {
                break;   // 起跳后首次落地
            }
        }
        assertThat(player.consumeFallDamage()).as("正常跳跃（含低重力跳）落地不应摔伤").isZero();
    }

    @Test
    void tallFallShouldDealCappedDamage() {
        LayerWorld world = tallWorld();
        PlayerEntity player = new PlayerEntity(100f, 900f);   // 高坠→达终端速度 700 →封顶
        for (int i = 0; i < 400; i++) {
            player.update(world, false, false, false, 1 / 60f);
        }
        assertThat(player.isOnGround()).isTrue();
        assertThat(player.consumeFallDamage()).isEqualTo(PlayerEntity.FALL_DMG_CAP);
        assertThat(player.consumeFallDamage()).as("取用后应清零").isZero();
    }

    /** 水体世界：y=0..9 石底，y=10..25 梦水，y>25 空气。水面顶在 tile 25。 */
    private static LayerWorld waterWorld() {
        LayerWorld world = new LayerWorld(32, 40);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 10; y++) {
                world.setBlock(x, y, BlockType.STONE);
            }
            for (int y = 10; y <= 25; y++) {
                world.setBlock(x, y, BlockType.WATER);
            }
        }
        return world;
    }

    @Test
    void shouldHoverStillWhenSubmergedNoInput() {
        LayerWorld world = waterWorld();
        PlayerEntity player = new PlayerEntity(100f, 288f);   // tile18 脚·完全浸没
        float y0 = player.y();
        for (int i = 0; i < 120; i++) {
            player.update(world, false, false, false, false, 1 / 60f);
        }
        assertThat(player.isInWater()).isTrue();
        assertThat(player.y()).as("中性浮力：无输入应悬浮不动").isCloseTo(y0, org.assertj.core.data.Offset.offset(0.5f));
        assertThat(player.consumeFallDamage()).isZero();
    }

    @Test
    void swimUpShouldRise() {
        LayerWorld world = waterWorld();
        PlayerEntity player = new PlayerEntity(100f, 288f);   // 深水中（头仍在 tile20 水下）
        float y0 = player.y();
        for (int i = 0; i < 30; i++) {
            player.update(world, false, false, true, false, 1 / 60f);   // 按住 W 上浮
        }
        assertThat(player.y()).as("水下按 W 应向上游动").isGreaterThan(y0 + 20f);
    }

    @Test
    void swimDownShouldSink() {
        LayerWorld world = waterWorld();
        PlayerEntity player = new PlayerEntity(100f, 320f);   // tile20 脚，上方仍有水
        float y0 = player.y();
        for (int i = 0; i < 30; i++) {
            player.update(world, false, false, false, true, 1 / 60f);   // 按住 S 下潜
        }
        assertThat(player.y()).as("按 S 应向下潜").isLessThan(y0 - 20f);
    }

    @Test
    void breachShouldLeapOutOfSurface() {
        LayerWorld world = waterWorld();
        PlayerEntity player = new PlayerEntity(100f, 384f);   // tile24 脚（水中）、头在 tile26 空气→露出水面
        float y0 = player.y();
        for (int i = 0; i < 12; i++) {
            player.update(world, false, false, true, false, 1 / 60f);   // 露头时按 W →跃出
        }
        assertThat(player.y()).as("露头按 W 应跃出水面（位移大于普通上浮）").isGreaterThan(y0 + 60f);
    }
}
