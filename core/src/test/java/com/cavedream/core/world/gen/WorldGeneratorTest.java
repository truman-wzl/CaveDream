package com.cavedream.core.world.gen;

import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerWorld;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 大世界生成器测试（tiny 壳，秒级）：四带一海结构、要素落位、连通性、种子确定性。 */
class WorldGeneratorTest {

    private static final GeneratedWorld GEN = WorldGenerator.generate(LayerLayout.tiny(), 42L);

    private static LayerWorld w() {
        return GEN.world();
    }

    @Test
    void borderShouldBeSealedWithFog() {
        LayerWorld w = w();
        assertThat(w.blockAt(0, 0)).isEqualTo(BlockType.FOG);
        assertThat(w.blockAt(w.getWidth() - 1, 0)).isEqualTo(BlockType.FOG);
        assertThat(w.blockAt(1, w.getHeight() - 1)).isEqualTo(BlockType.FOG);
    }

    @Test
    void verticalBandsShouldExistInOrder() {
        LayerWorld w = w();
        int cx = w.getWidth() / 2;
        // 底部=梦滓（避开中央竞技场，取 1/4 宽处）
        assertThat(w.blockAt(w.getWidth() / 4, 3)).isEqualTo(BlockType.HELL);
        // 中深=石头/泥土实体
        assertThat(w.blockAt(cx, 60).solid()).isTrue();
        // 天空带大部分为空
        assertThat(w.blockAt(30, w.getHeight() - 8)).isEqualTo(BlockType.AIR);
    }

    @Test
    void seaShouldHaveWaterAndSeafloorChest() {
        LayerWorld w = w();
        int water = 0;
        for (int x = 0; x < w.getWidth(); x++) {
            for (int y = 40; y < 140; y++) {
                if (w.blockAt(x, y) == BlockType.WATER) {
                    water++;
                }
            }
        }
        assertThat(water).as("梦水应成片存在").isGreaterThan(50);
        TilePos seaChest = GEN.chests().get(0);
        assertThat(seaChest.y()).as("首宝箱位于海床").isBetween(30, 50);
    }

    @Test
    void spawnShouldFloatAboveMainIslandWithGroundBelow() {
        LayerWorld w = w();
        TilePos s = GEN.spawn();
        assertThat(w.blockAt(s.x(), s.y())).isEqualTo(BlockType.AIR);
        boolean solidBelow = false;
        for (int d = 1; d <= 6; d++) {
            if (w.isSolid(s.x(), s.y() - d)) {
                solidBelow = true;
            }
        }
        assertThat(solidBelow).as("出生点脚下 6 格内有主浮岛").isTrue();
    }

    @Test
    void bossArenaShouldBeHollowAndReachableFromSpawn() {
        LayerWorld w = w();
        TilePos gate = GEN.bossGate();
        assertThat(w.blockAt(gate.x(), gate.y() - 10)).as("竞技场内部为空").isEqualTo(BlockType.AIR);
        assertThat(WorldGenerator.reachable(w, GEN.spawn(), gate))
                .as("出生点可四方向通行至 Boss 门").isTrue();
    }

    @Test
    void markersShouldMatchLayoutCounts() {
        LayerLayout L = LayerLayout.tiny();
        assertThat(GEN.pillars()).hasSize(L.pillarCount());
        assertThat(GEN.chests()).hasSizeGreaterThanOrEqualTo(L.chestCount());
        assertThat(GEN.narrativeSpots()).hasSize(L.narrativeCount());
        assertThat(GEN.gatheringSpots()).hasSize(L.gatheringCount());
    }

    @Test
    void sameSeedShouldGenerateSameWorld() {
        GeneratedWorld a = WorldGenerator.generate(LayerLayout.tiny(), 7L);
        GeneratedWorld b = WorldGenerator.generate(LayerLayout.tiny(), 7L);
        LayerWorld wa = a.world(), wb = b.world();
        int diff = 0;
        for (int x = 0; x < wa.getWidth(); x += 7) {
            for (int y = 0; y < wa.getHeight(); y += 7) {
                if (wa.blockAt(x, y) != wb.blockAt(x, y)) {
                    diff++;
                }
            }
        }
        assertThat(diff).isZero();
    }

    /** 树已改为非实体对象：世界内所有 TREE 格均非实心（不遮光/不断连通），且确对象树已生成。 */
    @Test
    void generatedTreesAreNonSolidObjects() {
        LayerWorld w = w();
        int tree = 0;
        for (int x = 0; x < w.getWidth(); x++) {
            for (int y = 0; y < w.getHeight(); y++) {
                if (w.blockAt(x, y) == BlockType.TREE) {
                    assertThat(w.blockAt(x, y).solid()).as("树必须非实体").isFalse();
                    tree++;
                }
            }
        }
        assertThat(tree).as("世界应生成对象树").isGreaterThan(0);
    }
}
