package com.cavedream.core.dream;

import java.util.List;

/**
 * 梦层定义（数据驱动，GDD §2 / §5）：
 * 主线层（L1–L5）来自手工配置，梦海层由 {@link ProceduralSeaSource} 运行时拼装。
 * 本对象是不可变静态数据；运行时可变的进度状态全部在 {@link WorldState}。
 *
 * @param id          层唯一标识（如 "L1_SHALLOW" / "sea_12"）
 * @param displayName 展示名
 * @param kind        主线层 / 梦海层
 * @param depth       深度：主线层 = 层序号（1..5）；梦海层 = 下潜的第 N 层
 * @param biomeId     生态主题（决定地形生成与素材集）
 * @param bossId      守层 Boss id，可为 null（锚点层无 Boss）
 * @param anchor      是否稳定锚点层（可建造/存档，GDD §2.2）
 * @param rules       本层挂载的法则（0..N 个）
 */
public record DreamPhase(
        String id,
        String displayName,
        Kind kind,
        int depth,
        String biomeId,
        String bossId,
        boolean anchor,
        List<IDreamRule> rules
) {

    public enum Kind {
        /** 主线层：有序下潜、手工配置 */
        STORYLINE,
        /** 元梦之海层：通关后程序生成 */
        DREAM_SEA
    }

    public DreamPhase {
        rules = List.copyOf(rules);
    }
}
