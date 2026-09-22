package com.cavedream.core.dream;

import java.util.List;
import java.util.Optional;

/**
 * 主线层来源（GDD §2.1）：按 L1→L5(E) 固定顺序提供手工配置的梦层。
 * <p>当前层表在代码内定义；M3 阶段迁移为 JSON 数据资产（GDD §7 数据驱动），
 * 类与接口不变，仅替换 {@link #defaultStoryline()} 的加载方式。
 */
public final class StorylineLayerSource implements DreamLayerSource {

    private final List<DreamPhase> layers;

    public StorylineLayerSource(List<DreamPhase> layers) {
        if (layers.isEmpty()) {
            throw new IllegalArgumentException("主线层配置不能为空");
        }
        this.layers = List.copyOf(layers);
    }

    /** 按 GDD §2.1 表构建默认主线（5 层，终层 E = Eternal）。 */
    public static StorylineLayerSource defaultStoryline() {
        return new StorylineLayerSource(List.of(
                storyline("L1_SHALLOW", "浅梦", 1, "childhood_ruins", "the_wanderer",
                        List.of()),
                storyline("L2_MAZE", "迷梦", 2, "moving_library", "maze_watcher",
                        List.of(new SimpleRule(DreamRuleIds.MAP_SHUFFLE))),
                storyline("L3_FALL", "坠梦", 3, "inverted_spire", "falling_shadow",
                        List.of(new SimpleRule(DreamRuleIds.GRAVITY_FLIP))),
                storyline("L4_NIGHTMARE", "魇梦", 4, "personal_hell", "dread_preimage",
                        List.of(new SimpleRule(DreamRuleIds.NIGHTMARE_TIDE))),
                storyline("L5_ETERNAL", "E · 永恒之境", 5, "seat_of_self", "inner_shadow",
                        List.of(new SimpleRule(DreamRuleIds.GRAVITY_FLIP),
                                new SimpleRule(DreamRuleIds.NIGHTMARE_TIDE),
                                new SimpleRule(DreamRuleIds.UNSTABLE_BLOCKS)))
        ));
    }

    private static DreamPhase storyline(String id, String name, int depth,
                                        String biome, String boss, List<IDreamRule> rules) {
        return new DreamPhase(id, name, DreamPhase.Kind.STORYLINE, depth, biome, boss, false, rules);
    }

    @Override
    public Optional<DreamPhase> next(WorldState state) {
        int index = state.getStorylineProgress();
        return index < layers.size() ? Optional.of(layers.get(index)) : Optional.empty();
    }

    /** 《回溯之书》按 id 回访已通关层用（GDD §4）。 */
    public Optional<DreamPhase> layerById(String id) {
        return layers.stream().filter(l -> l.id().equals(id)).findFirst();
    }

    public int totalLayers() {
        return layers.size();
    }
}
