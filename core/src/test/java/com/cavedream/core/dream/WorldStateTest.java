package com.cavedream.core.dream;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 主线状态机测试：L1→E 有序下潜、通关解锁梦海与《回溯之书》（GDD §2/§4/§5）。 */
class WorldStateTest {

    @Test
    void shouldAdvanceThroughAllStorylineLayersInOrder() {
        WorldState state = new WorldState(7L);
        StorylineLayerSource source = StorylineLayerSource.defaultStoryline();

        Optional<DreamPhase> layer = source.next(state);
        for (int i = 1; i <= source.totalLayers(); i++) {
            assertThat(layer).isPresent();
            DreamPhase current = layer.get();
            assertThat(current.depth()).isEqualTo(i);

            state.enter(current);
            layer = state.onBossDefeated(source);
        }

        assertThat(state.getStorylineProgress()).isEqualTo(5);
        assertThat(state.getClearedLayerIds()).contains("L1_SHALLOW", "L5_ETERNAL");
        // 击败自我心魔：梦境破碎 → 梦海与回溯之书解锁
        assertThat(state.isDreamSeaUnlocked()).isTrue();
        assertThat(state.isRetrospectionUnlocked()).isTrue();
    }

    @Test
    void shouldRejectBossProgressWithoutEnteringLayer() {
        WorldState state = new WorldState(1L);
        assertThatThrownBy(() -> state.onBossDefeated(StorylineLayerSource.defaultStoryline()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void shouldRejectDuplicateClearOfSameLayer() {
        WorldState state = new WorldState(1L);
        StorylineLayerSource source = StorylineLayerSource.defaultStoryline();
        DreamPhase l1 = source.next(state).orElseThrow();

        state.enter(l1);
        state.onBossDefeated(source);
        // 《回溯之书》回访旧层再打 Boss 不应重复记功
        state.enter(l1);
        assertThatThrownBy(() -> state.onBossDefeated(source))
                .isInstanceOf(IllegalStateException.class);
        assertThat(state.getStorylineProgress()).isEqualTo(1);
    }

    @Test
    void retrospectionShouldOnlyOpenClearedLayersAfterCompletion() {
        WorldState state = new WorldState(1L);
        StorylineLayerSource source = StorylineLayerSource.defaultStoryline();
        DreamPhase l1 = source.next(state).orElseThrow();
        state.enter(l1);
        state.onBossDefeated(source);

        // 未通关：拿不到回溯之书
        assertThat(state.canRetrospect("L1_SHALLOW")).isFalse();
    }
}
