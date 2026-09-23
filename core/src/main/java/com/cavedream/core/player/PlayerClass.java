package com.cavedream.core.player;

import com.cavedream.core.item.Item;

/**
 * 五职业（GDD 软职业"做梦人"）：选职业决定开局主武器（对应物品 id 200–204）与初始双条上限。
 * 武器精通随使用走、跨职业无惩罚；此处只定"开局给什么"。
 */
public enum PlayerClass {
    WARRIOR("战士", Item.WARRIOR_SWORD, 120, 10),
    MAGE("法师", Item.MAGE_WAND, 90, 20),
    SUMMONER("通灵者", Item.SUMMONER_STAFF, 100, 15),
    ARCHER("射手", Item.ARCHER_BOW, 100, 12),
    ASSASSIN("刺客", Item.ASSASSIN_DAGGER, 95, 12);

    private final String cn;
    private final Item weapon;
    private final int startLucidityMax;
    private final int startManaMax;

    PlayerClass(String cn, Item weapon, int startLucidityMax, int startManaMax) {
        this.cn = cn;
        this.weapon = weapon;
        this.startLucidityMax = startLucidityMax;
        this.startManaMax = startManaMax;
    }

    public String cn() {
        return cn;
    }

    public Item weapon() {
        return weapon;
    }

    public int startLucidityMax() {
        return startLucidityMax;
    }

    public int startManaMax() {
        return startManaMax;
    }
}
