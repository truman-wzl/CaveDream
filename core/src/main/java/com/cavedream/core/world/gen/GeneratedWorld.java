package com.cavedream.core.world.gen;

import com.cavedream.core.world.LayerWorld;

import java.util.List;

/**
 * 生成结果：tile 数据 + 骨架要素落位标记（§2.5 要素表）。
 * 标记先以坐标交付，实体化（梦锚柱/宝箱/工匠 NPC 渲染）在后续里程碑接入。
 */
public record GeneratedWorld(
        LayerWorld world,
        TilePos spawn,          // 出生点：主浮岛顶（坠入梦境从天落）
        TilePos dreamEntry,     // 入梦锚（主浮岛）
        TilePos bedSite,        // 床址预留地（入梦锚旁平地）
        TilePos dreamLoom,      // 织梦台（地表带）
        TilePos artisan,        // 梦之工匠游荡点（地表带）
        TilePos bossGate,       // Boss 门（地狱带中央）
        TilePos expansionVessel,// 扩容器藏匿点（洞穴带）
        List<TilePos> pillars,       // 梦锚柱
        List<TilePos> chests,        // 宝箱（含海底）
        List<TilePos> narrativeSpots,// 叙事碎片点
        List<TilePos> gatheringSpots,// 地表采集丛
        SeaSide seaSide,        // 海方位（§2.3 ≥1 侧，双侧 25%）
        int[] surfaceY          // 每列地表高度（背景画布分层用）
) {

    public enum SeaSide { LEFT, RIGHT, BOTH }
}
