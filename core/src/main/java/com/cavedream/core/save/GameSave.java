package com.cavedream.core.save;

import com.badlogic.gdx.utils.Json;

/**
 * 本地存档数据（可反复存/读测试）。世界由种子确定性生成，故只存"改动 diff + 玩家状态"。
 * 用 libGDX {@link Json} 序列化（纯数据、public 字段）。字段含义见名。
 */
public class GameSave {

    public String slot;                     // 存档槽位文件名（每档一个，新建不互盖）
    public String saveName = "";            // 存档显示名（创建时命名、列表可改名；与 slot 文件名解耦）
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
    public boolean codexOwned;              // 是否已向构梦者购得《世界准则法典》（一次性）
    public boolean guidePresent;            // 构梦者是否已现身（一旦现身就常驻，不因铸梦币下降而消失）

    public int faceTemplateId;              // 捏脸画板模板 id（0=梦之主）
    public int[] faceColors = new int[0];   // 玩家上色的外观（每格 ARGB，882 格）

    public String[] storyLog = new String[0];  // 本存档的“剧情记忆”（大模型经历记忆库，随存档持久化）

    public int[] chestTile = new int[0];       // 有记录的容器 tile 索引（任意存储摆件）
    public int[] chestCap = new int[0];        // 对应容量（变长，按此累加偏移切分下面两数组）
    public int[] chestItem = new int[0];       // 拼接：各容器逐格物品 id（-1 空）
    public int[] chestCount = new int[0];      // 拼接：对应数量

    public int[] paintItemId = new int[0];     // 被重绘外观的物品 id
    public int[] paintW = new int[0];          // 对应画布宽
    public int[] paintH = new int[0];          // 对应画布高
    public int[] paintColors = new int[0];     // 拼接：各物品逐格 ARGB（按 w*h 切分）

    public int[] mountIdx = new int[0];        // 墙面对象（火把等）格索引
    public int[] mountBlock = new int[0];      // 对应墙面对象 block id

    public int[] growAnchor = new int[0];      // 未长成树的锚点格索引（玩家种、随时间长高）
    public int[] growTargetH = new int[0];     // 对应目标高度
    public float[] growAge = new float[0];     // 对应已累积生长秒

    public int[] furnaceAnchor = new int[0];   // 熔炉锚点格索引
    public int[] fInItem = new int[0];         // 展平：每炉 4 输入格物品 id（-1 空）
    public int[] fInCount = new int[0];        // 对应输入数量
    public int[] fOutItem = new int[0];        // 展平：每炉 4 输出格物品 id（-1 空）
    public int[] fOutCount = new int[0];       // 对应输出数量

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
