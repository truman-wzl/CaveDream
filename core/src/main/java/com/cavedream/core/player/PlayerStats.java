package com.cavedream.core.player;

/**
 * 玩家双条资源（GDD §3.3）：梦眠=生命，魔能=技能燃料。
 * 回复曲线（v0.16.1 统一）：越满回得越快，速率 ≈ R×(当前/上限)²；
 * 战斗中：梦眠完全不回、魔能 25% 速率；脱战（视野内连续 ≥3 秒无敌）后正常回。
 * 梦眠归零 = 溃梦（Dream Break），由上层触发回锚点复活。纯逻辑、可单测。
 */
public final class PlayerStats {

    /** 满条时的每秒回复基准（待 M2 实测调）。 */
    private static final float LUCIDITY_REGEN_FULL = 8f;
    private static final float MANA_REGEN_FULL = 3f;
    /** 脱战阈值（秒）：受击后这么久无新伤害才算脱战。 */
    private static final float COMBAT_TIMEOUT = 3f;

    private final int maxLucidity;
    private final int maxMana;
    private int lucidity;
    private int mana;
    private float sinceCombat = Float.MAX_VALUE;
    private float lucAcc;
    private float manaAcc;
    private boolean dreamBreak;

    public PlayerStats(PlayerClass playerClass) {
        this.maxLucidity = playerClass.startLucidityMax();
        this.maxMana = playerClass.startManaMax();
        this.lucidity = maxLucidity;
        this.mana = maxMana;
    }

    public int maxLucidity() {
        return maxLucidity;
    }

    public int lucidity() {
        return lucidity;
    }

    public int maxMana() {
        return maxMana;
    }

    public int mana() {
        return mana;
    }

    public boolean inCombat() {
        return sinceCombat < COMBAT_TIMEOUT;
    }

    public boolean isDreamBreak() {
        return dreamBreak;
    }

    /** 受伤：扣梦眠并进入战斗态；归零触发溃梦。 */
    public void damage(int amount) {
        if (amount <= 0 || dreamBreak) {
            return;
        }
        lucidity = Math.max(0, lucidity - amount);
        sinceCombat = 0f;
        if (lucidity == 0) {
            dreamBreak = true;
        }
    }

    /** 释放技能耗魔能；不足则失败（不扣）。 */
    public boolean spendMana(int amount) {
        if (amount <= 0) {
            return true;
        }
        if (mana >= amount) {
            mana -= amount;
            return true;
        }
        return false;
    }

    /** 直接补魔能（采摘/消耗品瞬时补充）。 */
    public void restoreMana(int amount) {
        mana = Math.min(maxMana, mana + Math.max(0, amount));
    }

    /** 每帧推进回复（dt 秒）。 */
    public void update(float dt) {
        sinceCombat += dt;
        if (!dreamBreak) {
            if (!inCombat()) {
                lucAcc += LUCIDITY_REGEN_FULL * ratio2(lucidity, maxLucidity) * dt;
                int add = (int) lucAcc;
                if (add > 0) {
                    lucidity = Math.min(maxLucidity, lucidity + add);
                    lucAcc -= add;
                }
            }
        }
        float manaScale = inCombat() ? 0.25f : 1f;
        manaAcc += MANA_REGEN_FULL * manaScale * ratio2(mana, maxMana) * dt;
        int mad = (int) manaAcc;
        if (mad > 0) {
            mana = Math.min(maxMana, mana + mad);
            manaAcc -= mad;
        }
    }

    /** 溃梦后回锚点复活：恢复部分梦眠、满魔能、脱战。 */
    public void reviveAtAnchor() {
        lucidity = Math.max(1, (int) (maxLucidity * 0.5f));
        mana = maxMana;
        dreamBreak = false;
        sinceCombat = Float.MAX_VALUE;
        lucAcc = 0f;
        manaAcc = 0f;
    }

    private static double ratio2(int cur, int max) {
        if (max <= 0) {
            return 0;
        }
        double r = cur / (double) max;
        return r * r;
    }
}
