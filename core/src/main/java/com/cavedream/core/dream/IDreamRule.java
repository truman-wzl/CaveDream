package com.cavedream.core.dream;

/**
 * 梦境法则插件接口：每层梦携带的独特玩法单元（GDD §2 / §5）。
 * <p>扩展原则（GDD §5）：新增一条法则 = 新增一个实现类，不改核心系统。
 * 法则实现保持无状态，每层运行时通过 {@link DreamPhase#rules()} 挂载。
 */
public interface IDreamRule {

    /** 全局唯一标识；梦层 JSON 配置按此引用（数据驱动入口，GDD §7）。 */
    String id();

    /** 进入携带本法则的梦层时调用。 */
    default void onLayerEnter(DreamPhase layer) {
    }

    /** 每帧逻辑钩子，由游戏主循环调用；deltaSeconds 为帧间隔秒。 */
    default void onTick(DreamPhase layer, float deltaSeconds) {
    }
}
