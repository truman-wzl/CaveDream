package com.cavedream.core.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Disposable;

import java.util.Random;

/**
 * 动态视差天空（GDD P7）：随游戏时刻变化的天空渐变 + 日月升落弧线 + 漂移的云 + 夜空星点。
 * 全部程序化生成贴图（运行时、无外部素材）。以屏幕坐标绘制（uiCam），置于世界层之前。
 */
public final class SkyRenderer implements Disposable {

    // 时刻→(天顶色, 地平色) 关键帧，昼夜过渡线性插值
    private static final double[] KF_H = {0, 5, 6.5, 8, 12, 17, 19, 20.5, 24};
    private static final int[] KF_TOP = {
            0x05060F, 0x0A0C22, 0x3A2F5E, 0x3E6FB0, 0x3E78C8, 0x3E6FB0, 0x5A2F52, 0x0A0C22, 0x05060F};
    private static final int[] KF_BOT = {
            0x0E1030, 0x1A1636, 0xE8894A, 0x9FC6EA, 0xAED4F0, 0xC9A24A, 0xE0703A, 0x241A3A, 0x0E1030};

    private static final int GRAD_H = 256;
    private Texture gradient;
    private final int gradW = 4;
    private double lastKeyHour = -99;

    private Texture sun;
    private Texture moon;
    private Texture cloud;
    private Texture dot;
    private final float[] stars;              // x,y,相位（0~1 归一化屏幕）
    private final int starCount = 160;
    private final float[] clouds;             // 每朵：baseX, y, speed, scale
    private final int cloudCount = 6;

    public SkyRenderer() {
        rebuildGradient(12);
        sun = disc(128, 0xFFE38A, 0xFFF6C8, true);
        moon = disc(104, 0xD8DCF0, 0xFFFFFF, false);
        cloud = cloudTexture();
        dot = solidWhite();
        Random r = new Random(20260923);
        stars = new float[starCount * 3];
        for (int i = 0; i < stars.length; i += 3) {
            stars[i] = r.nextFloat();
            stars[i + 1] = r.nextFloat() * 0.7f;
            stars[i + 2] = r.nextFloat() * 6.28f;
        }
        clouds = new float[cloudCount * 4];
        for (int i = 0; i < clouds.length; i += 4) {
            clouds[i] = r.nextFloat();
            clouds[i + 1] = 0.12f + r.nextFloat() * 0.5f;   // 靠上天空
            clouds[i + 2] = 6f + r.nextFloat() * 10f;        // 漂移速度
            clouds[i + 3] = 0.7f + r.nextFloat() * 0.9f;     // 缩放
        }
    }

    /** 屏幕空间绘制（在画世界之前调用）。camX 用于云/星视差；time 秒；daylight01 0~1。 */
    public void draw(SpriteBatch b, float screenW, float screenH, float camX, float time,
                     float hour, float daylight01) {
        if (Math.abs(hour - lastKeyHour) > 0.05) {
            rebuildGradient(hour);
        }
        // 天空渐变铺满
        b.draw(gradient, 0, 0, screenW, screenH);

        // 星点（夜里才显）
        float starA = 1f - daylight01;
        if (starA > 0.02f) {
            for (int i = 0; i < stars.length; i += 3) {
                float sx = mod(stars[i] * screenW - camX * 0.02f, screenW);
                float sy = stars[i + 1] * screenH;
                float tw = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(time * 1.5 + stars[i + 2]));
                b.setColor(1, 1, 1, starA * tw * 0.9f);
                b.draw(dot, sx, sy, 1.6f, 1.6f);
            }
            b.setColor(1, 1, 1, 1);
        }

        // 日 / 月：6→18 太阳升落，其余月亮
        float body, alpha;
        Texture disc;
        if (hour >= 5.5f && hour <= 18.5f) {
            body = (hour - 5.5f) / 13f;               // 0..1 横跨
            disc = sun;
            alpha = 1f;
        } else {
            float mh = hour < 5.5f ? hour + 24 : hour;
            body = (mh - 18.5f) / 11f;
            disc = moon;
            alpha = 0.9f;
        }
        float bx = body * screenW;
        float by = screenH * (0.28f + 0.5f * (float) Math.sin(body * Math.PI));
        float ds = disc.getWidth();
        b.setColor(1, 1, 1, alpha);
        b.draw(disc, bx - ds / 2, by - ds / 2);
        b.setColor(1, 1, 1, 1);

        // 云：视差 + 循环漂移，白天更实、夜里偏暗
        float cw = cloud.getWidth(), ch = cloud.getHeight();
        float cloudA = 0.55f + 0.4f * daylight01;
        float shade = 0.5f + 0.5f * daylight01;
        for (int i = 0; i < clouds.length; i += 4) {
            float speed = clouds[i + 2], scale = clouds[i + 3];
            float x = mod(clouds[i] * (screenW + cw) + time * speed - camX * 0.12f, screenW + cw) - cw;
            float y = clouds[i + 1] * screenH;
            b.setColor(shade, shade, Math.min(1f, shade + 0.05f), cloudA);
            b.draw(cloud, x, y, cw * scale, ch * scale);
        }
        b.setColor(1, 1, 1, 1);
    }

    private void rebuildGradient(double hour) {
        lastKeyHour = hour;
        int[] pal = palette(hour);
        Pixmap pm = new Pixmap(gradW, GRAD_H, Pixmap.Format.RGB888);
        int top = pal[0], bot = pal[1];
        for (int y = 0; y < GRAD_H; y++) {
            float t = y / (float) (GRAD_H - 1);
            int r = (int) (((top >> 16 & 0xFF) * (1 - t) + (bot >> 16 & 0xFF) * t));
            int g = (int) (((top >> 8 & 0xFF) * (1 - t) + (bot >> 8 & 0xFF) * t));
            int bl = (int) (((top & 0xFF) * (1 - t) + (bot & 0xFF) * t));
            pm.setColor(r / 255f, g / 255f, bl / 255f, 1f);
            pm.drawLine(0, y, gradW - 1, y);
        }
        if (gradient != null) {
            gradient.dispose();
        }
        gradient = new Texture(pm);
        gradient.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
    }

    private static int[] palette(double hour) {
        for (int i = 0; i < KF_H.length - 1; i++) {
            if (hour >= KF_H[i] && hour <= KF_H[i + 1]) {
                float t = (float) ((hour - KF_H[i]) / (KF_H[i + 1] - KF_H[i]));
                return new int[]{lerp(KF_TOP[i], KF_TOP[i + 1], t), lerp(KF_BOT[i], KF_BOT[i + 1], t)};
            }
        }
        return new int[]{KF_TOP[0], KF_BOT[0]};
    }

    private static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16 & 0xFF) * (1 - t) + (b >> 16 & 0xFF) * t));
        int g = (int) (((a >> 8 & 0xFF) * (1 - t) + (b >> 8 & 0xFF) * t));
        int bl = (int) (((a & 0xFF) * (1 - t) + (b & 0xFF) * t));
        return r << 16 | g << 8 | bl;
    }

    private static Texture disc(int d, int edge, int core, boolean glow) {
        Pixmap pm = new Pixmap(d, d, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        float c = d / 2f;
        for (int y = 0; y < d; y++) {
            for (int x = 0; x < d; x++) {
                double dist = Math.hypot(x - c, y - c);
                if (dist < c * 0.62) {
                    set(pm, x, y, core, 255);
                } else if (dist < c * 0.72) {
                    set(pm, x, y, edge, 255);
                } else if (glow && dist < c) {
                    int a = (int) (90 * (1 - (dist - c * 0.72) / (c * 0.28)));
                    set(pm, x, y, edge, Math.max(0, a));
                }
            }
        }
        Texture t = new Texture(pm);
        t.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    private static Texture cloudTexture() {
        int w = 96, h = 34;
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        Random r = new Random(7);
        int[][] puffs = new int[10][];
        for (int i = 0; i < puffs.length; i++) {
            puffs[i] = new int[]{10 + r.nextInt(w - 20), h / 2 + r.nextInt(6) - 3, 8 + r.nextInt(8)};
        }
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int cover = 0;
                for (int[] p : puffs) {
                    double dist = Math.hypot(x - p[0], (y - p[1]) * 1.4);
                    if (dist < p[2]) {
                        cover = 255;
                    } else if (dist < p[2] + 3) {
                        cover = Math.max(cover, (int) (255 * (1 - (dist - p[2]) / 3)));
                    }
                }
                if (cover > 0) {
                    set(pm, x, y, 0xFFFFFF, cover);
                }
            }
        }
        Texture t = new Texture(pm);
        t.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    private static Texture solidWhite() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        Texture t = new Texture(pm);
        t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
        pm.dispose();
        return t;
    }

    private static void set(Pixmap pm, int x, int y, int rgb, int a) {
        pm.setColor(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f, a / 255f);
        pm.drawPixel(x, y);
    }

    private static float mod(float v, float m) {
        float r = v % m;
        return r < 0 ? r + m : r;
    }

    @Override
    public void dispose() {
        gradient.dispose();
        sun.dispose();
        moon.dispose();
        cloud.dispose();
        dot.dispose();
    }
}
