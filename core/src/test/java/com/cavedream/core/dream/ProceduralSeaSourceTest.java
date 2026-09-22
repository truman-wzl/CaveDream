package com.cavedream.core.dream;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 梦海程序生成测试：确定性持久、深度成长曲线、锚点/轮回Boss节奏（GDD §2.2）。 */
class ProceduralSeaSourceTest {

    private static List<String> ruleIds(DreamPhase phase) {
        return phase.rules().stream().map(IDreamRule::id).toList();
    }

    @Test
    void sameSeedAndDepthShouldGenerateIdenticalLayer() {
        ProceduralSeaSource a = new ProceduralSeaSource(42L);
        ProceduralSeaSource b = new ProceduralSeaSource(42L);

        DreamPhase layer1 = a.generate(9);
        DreamPhase layer2 = b.generate(9);

        // "随机但持久"（GDD §10-Q2）的前提：可重入生成
        assertThat(layer2).usingRecursiveComparison().isEqualTo(layer1);
    }

    @Test
    void differentSeedsShouldDiverge() {
        ProceduralSeaSource a = new ProceduralSeaSource(1L);
        ProceduralSeaSource b = new ProceduralSeaSource(2L);

        boolean anyDiff = false;
        for (int depth = 1; depth <= 10; depth++) {
            if (!a.generate(depth).biomeId().equals(b.generate(depth).biomeId())) {
                anyDiff = true;
                break;
            }
        }
        assertThat(anyDiff).as("不同存档种子应在若干深度上生成不同梦境").isTrue();
    }

    @Test
    void deeperLayersShouldCarryMoreRules() {
        ProceduralSeaSource source = new ProceduralSeaSource(42L);

        int shallowTotal = 0;
        int deepTotal = 0;
        for (int seedId = 1; seedId <= 20; seedId++) {
            ProceduralSeaSource s = new ProceduralSeaSource(seedId);
            shallowTotal += s.generate(1).rules().size();
            deepTotal += s.generate(30).rules().size();
        }

        // 深度越深法则越多（梦越扭曲），且始终不超过法则池上限、无重复
        assertThat(deepTotal).isGreaterThan(shallowTotal);
        assertThat(ruleIds(source.generate(30)))
                .doesNotHaveDuplicates()
                .hasSizeLessThanOrEqualTo(4);
    }

    @Test
    void anchorsAndEchoBossesShouldFollowCadence() {
        ProceduralSeaSource source = new ProceduralSeaSource(42L);

        assertThat(source.generate(5).anchor()).isTrue();
        assertThat(source.generate(10).anchor()).isTrue();
        assertThat(source.generate(5).bossId()).isNull();   // 锚点层无 Boss，可建造/存档
        assertThat(source.generate(4).bossId()).endsWith("_echo"); // 轮回 Boss
        assertThat(source.generate(3).bossId()).isNull();
    }

    @Test
    void seaShouldStayClosedBeforeStorylineCompletion() {
        WorldState state = new WorldState(42L);
        ProceduralSeaSource sea = new ProceduralSeaSource(state.getSeed());

        assertThat(sea.next(state)).isEmpty();
    }
}
