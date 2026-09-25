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

    private float homeX = Float.NaN;           // 巡逻中心（世界像素）
    private float patrolHalf = 0f;             // 巡逻半径
    private float dir = 1f;                     // 水平朝向
    private float pauseT = 0f;                  // 停顿计时
    private String bubble = "";                 // 语言泡文本
    private float bubbleT = 0f;                 // 语言泡剩余时长
    private float chatCd = 0f;                  // 下次冒泡冷却
    private static final float WALK_SPEED = 26f;

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

    /* ---------------- 自由走动 + 语言泡（NPC AI） ---------------- */

    /** 设定巡逻中心与半径（世界像素）。 */
    public void setHome(float homePx, float patrolHalfPx) {
        this.homeX = homePx;
        this.patrolHalf = patrolHalfPx;
    }

    /** 水平巡逻：在 [home-half, home+half] 间往返、随机停顿/换向（y 由上层贴地）。 */
    public void wander(float dt) {
        if (patrolHalf <= 0f || Float.isNaN(homeX)) {
            return;
        }
        if (pauseT > 0f) {
            pauseT -= dt;
            return;
        }
        x += dir * WALK_SPEED * dt;
        if (x > homeX + patrolHalf) {
            x = homeX + patrolHalf;
            dir = -1f;
            pauseT = nextPause();
        } else if (x < homeX - patrolHalf) {
            x = homeX - patrolHalf;
            dir = 1f;
            pauseT = nextPause();
        } else if (Math.random() < dt * 0.12f) {
            dir = -dir;
            pauseT = nextPause();
        }
    }

    private static float nextPause() {
        return 0.6f + (float) Math.random() * 1.8f;
    }

    public float facing() {
        return dir;
    }

    public void say(String line, float dur) {
        bubble = line == null ? "" : line;
        bubbleT = dur;
    }

    public boolean talking() {
        return bubbleT > 0f;
    }

    public String bubble() {
        return bubble;
    }

    /** 每帧计时：语言泡与冷却递减；当 engaged（相处够久）且冷却到点→返回 true 表示该冒一句新闲聊。 */
    public boolean tickChat(float dt, boolean engaged) {
        if (bubbleT > 0f) {
            bubbleT -= dt;
        }
        if (chatCd > 0f) {
            chatCd -= dt;
        }
        if (engaged && chatCd <= 0f) {
            chatCd = 5f + (float) Math.random() * 4f;
            return true;
        }
        return false;
    }
}
