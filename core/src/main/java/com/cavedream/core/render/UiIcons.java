package com.cavedream.core.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.item.Item;
import com.cavedream.core.player.PlayerClass;

import java.util.EnumMap;
import java.util.Map;

/**
 * 程序化 UI 图标（武器/星/月），纯像素绘制、零外部素材；日后可被 t_item_form 上传的真图替换。
 * 需在 GL 线程构造。
 */
public final class UiIcons implements Disposable {

    public static final int SIZE = 24;

    private final Map<PlayerClass, Texture> weapon = new EnumMap<>(PlayerClass.class);
    private Texture star;
    private Texture moon;
    private Texture pickaxe;
    private Texture axe;

    public UiIcons() {
        for (PlayerClass c : PlayerClass.values()) {
            weapon.put(c, buildWeapon(c));
        }
        star = buildStar();
        moon = buildMoon();
        pickaxe = buildPickaxe();
        axe = buildAxe();
    }

    public Texture weapon(PlayerClass c) {
        return weapon.get(c);
    }

    public Texture star() {
        return star;
    }

    public Texture moon() {
        return moon;
    }

    /** 按物品 id 选非方块物品图标（镐 100–105 / 斧 110–115 / 武器 200–204）；方块或未知返回 null（由调用方用块图）。 */
    public Texture iconFor(Item it) {
        if (it == null) {
            return null;
        }
        int id = it.id();
        if (id >= 100 && id <= 105) {
            return pickaxe;
        }
        if (id >= 110 && id <= 115) {
            return axe;
        }
        if (id >= 200 && id <= 204) {
            return weapon.get(PlayerClass.values()[id - 200]);
        }
        return null;
    }

    private static Texture toTexture(Pixmap pm) {
        Texture t = new Texture(pm);
        t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        pm.dispose();
        return t;
    }

    private static Pixmap canvas() {
        Pixmap pm = new Pixmap(SIZE, SIZE, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        return pm;
    }

    private static void px(Pixmap pm, int x, int y, int rgb, int a) {
        if (x < 0 || y < 0 || x >= SIZE || y >= SIZE) {
            return;
        }
        pm.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a / 255f);
        pm.drawPixel(x, y);
    }

    private static Texture buildWeapon(PlayerClass c) {
        Pixmap pm = canvas();
        int wood = 0x8A5A2B, metal = 0xC8CDD8, leaf = 0x4C8A3E, glow = 0x8E7CC3, dark = 0x2A2140;
        switch (c) {
            case WARRIOR -> {                 // 木剑：斜刃 + 护手 + 柄
                for (int i = 3; i < 18; i++) {
                    px(pm, i, i, metal, 255);
                    px(pm, i + 1, i, 0xE6EAF0, 255);
                }
                for (int i = -2; i <= 2; i++) {
                    px(pm, 5 + i, 7 - i, wood, 255);   // 护手
                }
                px(pm, 3, 3, wood, 255);
                px(pm, 2, 2, wood, 255);                // 柄头
            }
            case MAGE -> {                    // 自然法杖：竖杖 + 顶端叶球
                for (int y = 4; y < SIZE; y++) {
                    px(pm, 8, y, wood, 255);
                    px(pm, 9, y, 0xA8703A, 255);
                }
                fillCircle(pm, 9, 5, 4, leaf);
                fillCircle(pm, 9, 5, 2, 0x67B34A);
            }
            case ARCHER -> {                  // 木弓：弧 + 弦
                for (int y = 2; y < SIZE - 2; y++) {
                    int x = 6 + (int) (Math.sin((y - 2) / (float) (SIZE - 4) * Math.PI) * 9);
                    px(pm, x, y, wood, 255);
                }
                for (int y = 3; y < SIZE - 3; y++) {
                    px(pm, 7, y, 0xDDDDB0, 255);        // 弦
                }
            }
            case SUMMONER -> {                // 自然之召唤：法典 + 符印
                for (int x = 5; x < 19; x++) {
                    for (int y = 4; y < 20; y++) {
                        px(pm, x, y, 0x6B4A2F, 255);
                    }
                }
                for (int y = 4; y < 20; y++) {
                    px(pm, 5, y, dark, 255);            // 书脊
                }
                fillCircle(pm, 12, 12, 3, glow);        // 符印
            }
            case ASSASSIN -> {                // 自然球：圆球 + 高光
                fillCircle(pm, 12, 12, 8, glow);
                fillCircle(pm, 12, 12, 6, 0x6E5DA6);
                px(pm, 9, 9, 0xFFFFFF, 220);
                px(pm, 10, 9, 0xFFFFFF, 160);
            }
        }
        return toTexture(pm);
    }

    private static Texture buildPickaxe() {
        Pixmap pm = canvas();
        int wood = 0x8A5A2B, head = 0xB8BCC6;
        for (int i = 5; i < 20; i++) {                 // 斜柄
            px(pm, i, i, wood, 255);
            px(pm, i + 1, i, 0xA8703A, 255);
        }
        for (int x = 4; x <= 18; x++) {                // 镐头（顶部弧）
            int y = 18 - (int) (Math.abs(x - 11) * 0.7);
            px(pm, x, y, head, 255);
            px(pm, x, y - 1, head, 255);
        }
        return toTexture(pm);
    }

    private static Texture buildAxe() {
        Pixmap pm = canvas();
        int wood = 0x8A5A2B, head = 0xB8BCC6;
        for (int i = 5; i < 21; i++) {                 // 斜柄
            px(pm, i, i, wood, 255);
        }
        for (int y = 12; y <= 20; y++) {               // 斧刃（左上块）
            for (int x = 4; x <= 10; x++) {
                if (x + (20 - y) <= 16) {
                    px(pm, x, y, head, 255);
                }
            }
        }
        return toTexture(pm);
    }

    private static void fillCircle(Pixmap pm, int cx, int cy, int r, int rgb) {
        for (int y = cy - r; y <= cy + r; y++) {
            for (int x = cx - r; x <= cx + r; x++) {
                double dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r * r + r) {
                    px(pm, x, y, rgb, 255);
                }
            }
        }
    }

    private static Texture buildStar() {
        Pixmap pm = canvas();
        int c = 0x4FA0FF;
        int cx = 12, cy = 12;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double dx = x - cx, dy = y - cy;
                double ang = Math.atan2(dy, dx);
                double r5 = 3.5 + 8.5 * Math.pow(Math.abs(Math.cos(2.5 * ang)), 1.6); // 五角星轮廓
                if (Math.hypot(dx, dy) <= r5) {
                    px(pm, x, y, Math.hypot(dx, dy) > r5 - 1.5 ? 0x2E6FBF : c, 255);
                }
            }
        }
        return toTexture(pm);
    }

    private static Texture buildMoon() {
        Pixmap pm = canvas();
        int cx = 12, cy = 12, r = 9;
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (d <= r) {
                    // 满月：亮黄 + 右上缺角成眉月感（这里画实心月，缺由 alpha 表示填充度）
                    int col = d > r - 1.5 ? 0xC9A94A : (x + y > 16 ? 0xF5D76E : 0xFFF6C8);
                    px(pm, x, y, col, 255);
                }
            }
        }
        return toTexture(pm);
    }

    @Override
    public void dispose() {
        for (Texture t : weapon.values()) {
            t.dispose();
        }
        star.dispose();
        moon.dispose();
        pickaxe.dispose();
        axe.dispose();
    }
}
