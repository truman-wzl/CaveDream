package com.cavedream.core.world.npc;

import com.cavedream.core.render.PaintedLook;

/**
 * 构梦者（向导/商人 NPC，GDD §3.10）：玩家铸梦币达到阈值后自动出现在村庄周围；
 * 右键对话开商店，花固定价购买《世界准则法典》（一次性，作为解锁三类画板的权限，非背包物品）。
 * 外观是 {@link PaintedLook}（模板 id=1），由玩家在剧情触发的画板中"捏脸"成形。纯逻辑、可单测。
 */
public final class GuideNpc {

    public static final int SPAWN_COINS = 10;      // 铸梦币达此值→出现
    public static final int CODEX_PRICE = 50;      // 世界准则法典售价
    public static final int TEMPLATE_ID = 1;       // 构梦者画板模板 id

    private float x;
    private float y;
    private boolean present;                       // 是否已出现在世界中
    private boolean codexOwned;                    // 法典是否已被购得（一次性）
    private PaintedLook look = new PaintedLook(TEMPLATE_ID);

    /** 放到某像素坐标（左下角，与怪一致）。 */
    public void place(float px, float py) {
        this.x = px;
        this.y = py;
    }

    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public boolean present() {
        return present;
    }

    public void setPresent(boolean p) {
        this.present = p;
    }

    public boolean codexOwned() {
        return codexOwned;
    }

    public void setCodexOwned(boolean owned) {
        this.codexOwned = owned;
    }

    public PaintedLook look() {
        return look;
    }

    public void setLook(PaintedLook look) {
        this.look = look;
    }

    /** 铸梦币够触发出现的阈值？ */
    public static boolean reachesSpawn(int coins) {
        return coins >= SPAWN_COINS;
    }

    /** 现在能否购买法典：已出现、未购得、且币够。 */
    public boolean canSellCodex(int coins) {
        return present && !codexOwned && coins >= CODEX_PRICE;
    }

    /** 成交法典（扣费由上层做）。已购得则失败。 */
    public boolean sellCodex() {
        if (codexOwned) {
            return false;
        }
        codexOwned = true;
        return true;
    }
}
