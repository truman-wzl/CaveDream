package com.cavedream.core.dream;

/**
 * 通用无行为法则占位实现：MVP 阶段各法则在 {@link #onLayerEnter} / {@link #onTick}
 * 中填入真实逻辑。当前用于把"法则可挂载、可枚举、可序列化"的管线先跑通。
 */
public record SimpleRule(String id) implements IDreamRule {
}
