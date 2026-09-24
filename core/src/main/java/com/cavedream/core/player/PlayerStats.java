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
    /** 受击无敌帧时长（基础机制，与怪一致）。 */
    private static final float PLAYER_IFRAME = 0.8f;

    private final int maxLucidity;
    private final int maxMana;
    private int lucidity;
    private int mana;
    private float sinceCombat = Float.MAX_VALUE;
    private float lucAcc;
    private float manaAcc;
    private boolean dreamBreak;
    private float invulnT;                                                    // 受击无敌帧计时

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

    /** 存档恢复：直接设定当前值（钳到 [0,上限]）。 */
    public void setLucidity(int v) {
        lucidity = Math.max(0, Math.min(maxLucidity, v));
        dreamBreak = lucidity == 0;
    }

    public void setMana(int v) {
        mana = Math.max(0, Math.min(maxMana, v));
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

    /** 受击统一入口（基类机制）：无敌帧内不受伤，否则扣血并进入无敌帧。返回是否吃到伤害。 */
    public boolean hurt(int amount) {
        if (invulnT > 0f || dreamBreak) {
            return false;
        }
        invulnT = PLAYER_IFRAME;
        damage(amount);
        return true;
    }

    /** 是否处于受击无敌帧（供渲染闪白）。 */
    public boolean isInvulnerable() {
        return invulnT > 0f;
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
        if (invulnT > 0f) {
            invulnT -= dt;
        }
        if (!dreamBreak) {
            if (!inCombat()) {
                lucAcc += LUCIDITY_REGEN_FULL * regenCurve(lucidity, maxLucidity) * dt;
                int add = (int) lucAcc;
                if (add > 0) {
                    lucidity = Math.min(maxLucidity, lucidity + add);
                    lucAcc -= add;
                }
            }
        }
        float manaScale = inCombat() ? 0.25f : 1f;
        manaAcc += MANA_REGEN_FULL * manaScale * regenCurve(mana, maxMana) * dt;
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
        invulnT = 0f;
        lucAcc = 0f;
        manaAcc = 0f;
    }

    /** 回复曲线：越满越快（二次），但带 15% 地板→再低也不会完全停滞。 */
    private static double regenCurve(int cur, int max) {
        if (max <= 0) {
            return 0;
        }
        double r = cur / (double) max;
        return 0.15 + 0.85 * r * r;
    }
}
