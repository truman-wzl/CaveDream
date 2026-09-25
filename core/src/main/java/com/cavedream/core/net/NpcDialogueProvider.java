package com.cavedream.core.net;

/**
 * NPC 对话供给（GDD 理念：对话由大模型按"大纲"实时生成，非写死）。
 * 默认走智谱开放平台 `glm-4-flash`（免费档、OpenAI 兼容）；base url / 模型 / key 全走环境变量，换供应商零改码。
 * key 只从环境变量读（ZHIPU_API_KEY 或 LLM_API_KEY），绝不写死。无 key 或失败 → 离线兜底台词，功能不卡死。
 */
public final class NpcDialogueProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;

    public NpcDialogueProvider() {
        this.baseUrl = cfg("LLM_BASE_URL", "https://open.bigmodel.cn/api/paas/v4");
        String k = System.getenv("ZHIPU_API_KEY");
        if (k == null || k.isBlank()) {
            k = System.getenv("LLM_API_KEY");
        }
        this.apiKey = k;
        this.model = cfg("LLM_MODEL", "glm-4-flash");
    }

    public boolean available() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** 同步生成（须由上层放到后台线程调用）。无 key 或失败则返回 fallback。 */
    public String reply(String persona, String playerLine, String fallback) {
        if (!available()) {
            return fallback;
        }
        String r = LlmClient.chat(baseUrl, apiKey, model, persona, playerLine);
        return r == null || r.isBlank() ? fallback : r;
    }

    /** 构梦者人设（由 GDD 大纲浓缩而来）：神秘、超然、引导玩家"改写现实"。 */
    public String guidePersona(boolean codexOwned) {
        return guidePersona(codexOwned, null);
    }
    
    /** 带“经历记忆”的人设：memory = 本存档的 storyLog，让 NPC 记得与玩家的历史。 */
    public String guidePersona(boolean codexOwned, String memory) {
        String s = "你是游戏《梦迹行者》中的NPC「构梦者」——一位在梦境村庄旁出没的神秘商人/向导。"
                + "性格：超然、温和、略带禅意与俏皮，说话简短（1-2句、不超过40字），中文，不用列表、不加引号。"
                + "你出售《世界准则法典》，它能让做梦者改写梦境的规则（重塑样貌、方块与武器）。"
                + (codexOwned
                    ? "当前玩家已购得法典，可鼓励他多去创作、探索梦境。"
                    : "当前玩家尚未购得法典，可神秘地引诱他来商店购买（售价50铸梦币）。");
        if (memory != null && !memory.isBlank()) {
            s += "【这个梦已经发生过的事】" + memory + "。可自然呼应这些经历（但不要逐条复述、不要编造未发生的）。";
        }
        return s;
    }

    private static String cfg(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }
}
