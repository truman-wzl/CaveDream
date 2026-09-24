package com.cavedream.core.save;

import com.badlogic.gdx.utils.Json;

/**
 * 本地存档数据（可反复存/读测试）。世界由种子确定性生成，故只存"改动 diff + 玩家状态"。
 * 用 libGDX {@link Json} 序列化（纯数据、public 字段）。字段含义见名。
 */
public class GameSave {

    public String slot;                     // 存档槽位文件名（每档一个，新建不互盖）
    public String className;
    public long seed;                       // 世界种子（long，与生成一致；截断会重建出不同地形→悬空摔落）

    public int[] editIdx = new int[0];      // 被改动格的一维索引
    public int[] editBlock = new int[0];    // 对应方块 id（与 editIdx 平行）

    public float playerX;
    public float playerY;

    public int[] invItem = new int[0];      // 各槽物品 id（-1 空）
    public int[] invCount = new int[0];     // 各槽数量
    public int invSelected;

    public int lucidity;
    public int maxLucidity;
    public int mana;
    public int maxMana;

    public int clockMinutes;                // 昼夜时刻（自 0:00 的分钟）
    public int coins;                       // 铸梦币
    public int servantCap;                  // 召唤师仆从上限（存上限不存当前数；重进存档需重新召唤）

    public String toJson() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        return json.toJson(this);
    }

    public static GameSave fromJson(String s) {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        return json.fromJson(GameSave.class, s);
    }
}
