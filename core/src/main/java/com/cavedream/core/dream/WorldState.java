package com.cavedream.core.dream;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * 世界进度权威记录（GDD §5）：主线推进、梦海深度、解锁旗标。
 * <p>后续以 JSON 序列化进存档（Jackson，GDD §7）。
 * <p>Boss 闸门 {@link #onBossDefeated} 是唯一推进入口；层来源只"提供"层，
 * 状态机负责"消费"层——主线/梦海共用同一套逻辑。
 */
public final class WorldState {

    /** 存档种子：梦海程序生成的确定性来源 */
    private final long seed;

    private DreamPhase currentLayer;
    private int storylineProgress;
    private int dreamSeaDepth;
    private boolean dreamSeaUnlocked;
    private boolean retrospectionUnlocked;
    private final Set<String> clearedLayerIds = new LinkedHashSet<>();

    public WorldState(long seed) {
        this.seed = seed;
    }

    /** 进入一个梦层。 */
    public void enter(DreamPhase layer) {
        this.currentLayer = layer;
    }

    /**
     * Boss 闸门（GDD §5）：记功并返回下一个应下潜的梦层。
     * <p>主线最后一层（Eternal）全通后 source.next 为空 —— 此刻梦境破碎：
     * 解锁元梦之海与《回溯之书》（GDD §2.2 / §4），返回 empty 交由游戏循环切换层来源。
     */
    public Optional<DreamPhase> onBossDefeated(DreamLayerSource source) {
        if (currentLayer == null) {
            throw new IllegalStateException("当前不在任何梦层，无法推进 Boss 进度");
        }
        DreamPhase defeated = currentLayer;
        if (defeated.bossId() == null) {
            throw new IllegalStateException("锚点/无主层没有 Boss 闸门：" + defeated.id());
        }
        if (!clearedLayerIds.add(defeated.id())) {
            throw new IllegalStateException("该层已通关，不允许重复记功：" + defeated.id());
        }

        if (defeated.kind() == DreamPhase.Kind.STORYLINE) {
            storylineProgress++;
        } else {
            dreamSeaDepth++;
        }

        Optional<DreamPhase> next = source.next(this);
        if (next.isEmpty() && defeated.kind() == DreamPhase.Kind.STORYLINE && !dreamSeaUnlocked) {
            dreamSeaUnlocked = true;
            retrospectionUnlocked = true;   // 通关奖励：《回溯之书》
        }
        return next;
    }

    /** 《回溯之书》：回溯到任意已通关主线层（GDD §4）。 */
    public boolean canRetrospect(String layerId) {
        return retrospectionUnlocked && clearedLayerIds.contains(layerId);
    }

    public long getSeed() {
        return seed;
    }

    public Optional<DreamPhase> getCurrentLayer() {
        return Optional.ofNullable(currentLayer);
    }

    public int getStorylineProgress() {
        return storylineProgress;
    }

    public int getDreamSeaDepth() {
        return dreamSeaDepth;
    }

    public boolean isDreamSeaUnlocked() {
        return dreamSeaUnlocked;
    }

    public boolean isRetrospectionUnlocked() {
        return retrospectionUnlocked;
    }

    /** 已通关层 id（只读视图，存档/回溯判定用）。 */
    public Set<String> getClearedLayerIds() {
        return Collections.unmodifiableSet(clearedLayerIds);
    }
}
