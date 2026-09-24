package com.cavedream.core.player;

import com.cavedream.core.item.Item;

/**
 * 五职业（GDD 软职业"做梦人"）：选职业决定开局主武器（对应物品 id 200–204）与初始双条上限。
 * 武器精通随使用走、跨职业无惩罚；此处只定"开局给什么"。
 */
public enum PlayerClass {
    WARRIOR("战士", Item.WARRIOR_SWORD),
    MAGE("法师", Item.MAGE_WAND),
    SUMMONER("通灵者", Item.SUMMONER_STAFF),
    ARCHER("射手", Item.ARCHER_BOW),
    ASSASSIN("刺客", Item.ASSASSIN_DAGGER);

    /** 全职业统一初始上限（GDD §3.3：梦眠 100、魔能 10）。 */
    public static final int START_LUCIDITY_MAX = 100;
    public static final int START_MANA_MAX = 10;

    private final String cn;
    private final Item weapon;

    PlayerClass(String cn, Item weapon) {
        this.cn = cn;
        this.weapon = weapon;
    }

    public String cn() {
        return cn;
    }

    public Item weapon() {
        return weapon;
    }

    public int startLucidityMax() {
        return START_LUCIDITY_MAX;
    }

    public int startManaMax() {
        return START_MANA_MAX;
    }
}
