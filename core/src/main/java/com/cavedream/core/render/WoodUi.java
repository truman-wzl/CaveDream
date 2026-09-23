package com.cavedream.core.render;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * 复古木质 UI 皮肤（程序化绘制，无外部素材）：木板面板 + 凸起木条按钮 + 木纹。
 * 所有菜单用它统一"木质公告牌"观感；调用方需已 batch.begin()。
 */
public final class WoodUi {

    private static final int WOOD = 0x6B4A2F;
    private static final int WOOD_HI = 0x8A5A34;
    private static final int WOOD_LO = 0x3A281A;
    private static final int EDGE = 0x241608;

    private WoodUi() {
    }

    /** 木质公告牌面板：木色底 + 深色外框 + 顶部高光 + 横向木纹。 */
    public static void panel(SpriteBatch b, Texture px, float x, float y, float w, float h) {
        rect(b, px, x, y, w, h, WOOD, 1f);
        // 木纹（横向暗带，按 y 周期）
        for (float gy = y + 6; gy < y + h - 2; gy += 10) {
            rect(b, px, x + 2, gy, w - 4, 1, WOOD_LO, 0.18f);
        }
        // 顶部高光、底部暗边（立体感）
        rect(b, px, x, y + h - 2, w, 2, WOOD_HI, 0.6f);
        rect(b, px, x, y, w, 2, WOOD_LO, 0.7f);
        // 外框描边
        border(b, px, x, y, w, h, EDGE, 2f);
    }

    /** 凸起的木条按钮：比面板更亮的木板 + 上高光下阴影 + 描边；hover 时整体提亮。 */
    public static void plank(SpriteBatch b, Texture px, float x, float y, float w, float h, boolean hover) {
        int base = hover ? 0x9A6A3A : WOOD_HI;
        rect(b, px, x, y, w, h, base, 1f);
        for (float gy = y + 5; gy < y + h - 2; gy += 8) {
            rect(b, px, x + 2, gy, w - 4, 1, WOOD_LO, 0.14f);
        }
        rect(b, px, x, y + h - 2, w, 2, 0xC08A50, hover ? 0.9f : 0.6f);   // 顶高光（凸起）
        rect(b, px, x, y, w, 3, WOOD_LO, 0.85f);                          // 底阴影
        rect(b, px, x, y, 2, h, WOOD_LO, 0.4f);
        rect(b, px, x + w - 2, y, 2, h, WOOD_LO, 0.4f);
        border(b, px, x, y, w, h, EDGE, 2f);
    }

    /** 大正方形卡片（选职业用）：木框 + 内凹深色底，供叠放武器图标。 */
    public static void card(SpriteBatch b, Texture px, float x, float y, float size, boolean hover, boolean selected) {
        plank(b, px, x, y, size, size, hover);
        float pad = 6;
        rect(b, px, x + pad, y + pad, size - 2 * pad, size - 2 * pad, 0x1A120A, 0.85f);  // 内凹底
        if (selected) {
            border(b, px, x + 2, y + 2, size - 4, size - 4, 0xFFE14D, 2f);
        }
    }

    private static void rect(SpriteBatch b, Texture px, float x, float y, float w, float h, int rgb, float a) {
        b.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a);
        b.draw(px, x, y, w, h);
    }

    private static void border(SpriteBatch b, Texture px, float x, float y, float w, float h, int rgb, float t) {
        rect(b, px, x, y, w, t, rgb, 1f);
        rect(b, px, x, y + h - t, w, t, rgb, 1f);
        rect(b, px, x, y, t, h, rgb, 1f);
        rect(b, px, x + w - t, y, t, h, rgb, 1f);
    }
}
