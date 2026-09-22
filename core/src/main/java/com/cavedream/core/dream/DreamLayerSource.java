package com.cavedream.core.dream;

import java.util.Optional;

/**
 * 梦层来源（GDD §5）：主线与梦海共用同一抽象，只是"数据来源"不同。
 * 主线层读手工配置（有序）；梦海层运行时随机组合法则/生态/Boss（无限）。
 */
public interface DreamLayerSource {

    /**
     * 依据当前世界进度给出下一个应下潜的梦层。
     *
     * @return empty 表示当前来源没有自然的下一层（如主线已全通）
     */
    Optional<DreamPhase> next(WorldState state);
}
