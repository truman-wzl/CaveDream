package com.cavedream.core.dream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

/**
 * 元梦之海来源（GDD §2.2）：通关后按"法则池 × 生态池 × Boss池"随机组合，
 * 为每个深度生成一个可重入的持久梦层（随机但持久，见 GDD §10-Q2）。
 * <p>确定性：以 存档种子+深度 派生随机序列，同一存档的同一深度永远生成同一层，
 * 因此随机层可以进存档、能反复下潜。
 * <p>无限更新的落点：内容更新只需向三个池子里加元素（新增法则实现/Biome/Boss），
 * 组合空间自动膨胀，本类与核心系统不改。
 */
public final class ProceduralSeaSource implements DreamLayerSource {

    /** 生态主题池（占位 id，M5 接入地形生成器） */
    private static final List<String> BIOME_POOL = List.of(
            "frozen_time",           // 凝固时刻
            "inverted_city",         // 倒悬之城
            "mirror_wasteland",      // 镜面荒原
            "forgotten_nursery",     // 遗忘育室
            "endless_staircase"      // 无尽阶梯
    );

    /** 轮回 Boss 池：主线 Boss 的变体形态（GDD §2.2），每 4 层出现一次 */
    private static final List<String> ECHO_BOSS_POOL = List.of(
            "wanderer_echo", "maze_watcher_echo", "falling_shadow_echo", "dread_preimage_echo"
    );

    /** 锚点层间隔：每 5 层一个稳定层，可建造/存档（防疲劳骨架） */
    private static final int ANCHOR_INTERVAL = 5;
    private static final int BOSS_INTERVAL = 4;

    private final long saveSeed;
    private final List<Supplier<IDreamRule>> rulePool;
    private final List<String> biomePool;
    private final List<String> echoBossPool;

    public ProceduralSeaSource(long saveSeed) {
        this(saveSeed, defaultRulePool(), BIOME_POOL, ECHO_BOSS_POOL);
    }

    public ProceduralSeaSource(long saveSeed,
                               List<Supplier<IDreamRule>> rulePool,
                               List<String> biomePool,
                               List<String> echoBossPool) {
        if (rulePool.isEmpty() || biomePool.isEmpty()) {
            throw new IllegalArgumentException("梦海生成池不能为空");
        }
        this.saveSeed = saveSeed;
        this.rulePool = List.copyOf(rulePool);
        this.biomePool = List.copyOf(biomePool);
        this.echoBossPool = List.copyOf(echoBossPool);
    }

    private static List<Supplier<IDreamRule>> defaultRulePool() {
        return List.of(
                () -> new SimpleRule(DreamRuleIds.MAP_SHUFFLE),
                () -> new SimpleRule(DreamRuleIds.GRAVITY_FLIP),
                () -> new SimpleRule(DreamRuleIds.NIGHTMARE_TIDE),
                () -> new SimpleRule(DreamRuleIds.UNSTABLE_BLOCKS)
        );
    }

    @Override
    public Optional<DreamPhase> next(WorldState state) {
        if (!state.isDreamSeaUnlocked()) {
            return Optional.empty(); // 未通关主线，梦海不开放
        }
        return Optional.of(generate(state.getDreamSeaDepth() + 1));
    }

    /** 生成第 depth 层梦海（depth 从 1 起）。同种子同深度结果确定，可持久化。 */
    public DreamPhase generate(int depth) {
        if (depth < 1) {
            throw new IllegalArgumentException("梦海深度从 1 起：" + depth);
        }
        Random rnd = new Random(saveSeed * 1_000_003L + depth);

        // 法则：深度越深，挂载越多（扭曲加剧），上限受池子大小约束
        int maxRules = Math.min(2 + depth / 3, rulePool.size());
        int ruleCount = 1 + rnd.nextInt(maxRules);
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < rulePool.size(); i++) {
            indexes.add(i);
        }
        Collections.shuffle(indexes, rnd);
        List<IDreamRule> rules = new ArrayList<>();
        for (int i = 0; i < ruleCount; i++) {
            rules.add(rulePool.get(indexes.get(i)).get());
        }

        boolean anchor = depth % ANCHOR_INTERVAL == 0;
        String bossId = (anchor || echoBossPool.isEmpty()) ? null
                : (depth % BOSS_INTERVAL == 0
                    ? echoBossPool.get(rnd.nextInt(echoBossPool.size()))
                    : null);
        String biome = anchor ? "sanctum" : biomePool.get(rnd.nextInt(biomePool.size()));

        return new DreamPhase(
                "sea_" + depth,
                (anchor ? "梦之锚点" : "梦境碎片") + " #" + depth,
                DreamPhase.Kind.DREAM_SEA,
                depth,
                biome,
                bossId,
                anchor,
                rules
        );
    }
}
