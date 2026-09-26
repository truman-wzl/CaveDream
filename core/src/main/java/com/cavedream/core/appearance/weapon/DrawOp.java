package com.cavedream.core.appearance.weapon;

/**
 * 图元（受限词表，保证 AI/手写产出可控可校验）。坐标在武器画布空间（y 向下，尾在下、刃朝上）。
 * pts 语义随 kind：POLY=[x0,y0,...]；LINE=[x0,y0,x1,y1]；ARC=[cx,cy,r,a0Deg,a1Deg]；CIRCLE=[cx,cy,r]；
 * GLOW=[cx,cy,r]；GRAIN=[x0,y0,...]（多边形内叠噪声）。color=0 → 用部件材质色。
 */
public final class DrawOp {

    public static final byte POLY_FILL = 0, POLY_STROKE = 1, LINE = 2, ARC = 3, CIRCLE = 4, GLOW = 5, GRAIN = 6;

    public byte kind;
    public int[] pts;
    public int color;       // 0 = 用材质
    public int width;       // 线/描边/弧 粗细
    public int intensity;   // GLOW alpha 0..255

    public DrawOp() {
    }

    private DrawOp(byte kind, int[] pts, int color, int width, int intensity) {
        this.kind = kind;
        this.pts = pts;
        this.color = color;
        this.width = width;
        this.intensity = intensity;
    }

    public static DrawOp polyFill(int color, int... pts) {
        return new DrawOp(POLY_FILL, pts, color, 0, 0);
    }

    public static DrawOp polyStroke(int color, int width, int... pts) {
        return new DrawOp(POLY_STROKE, pts, color, width, 0);
    }

    public static DrawOp line(int color, int width, int x0, int y0, int x1, int y1) {
        return new DrawOp(LINE, new int[]{x0, y0, x1, y1}, color, width, 0);
    }

    public static DrawOp arc(int color, int width, int cx, int cy, int r, int a0Deg, int a1Deg) {
        return new DrawOp(ARC, new int[]{cx, cy, r, a0Deg, a1Deg}, color, width, 0);
    }

    public static DrawOp circle(int color, int cx, int cy, int r) {
        return new DrawOp(CIRCLE, new int[]{cx, cy, r}, color, 0, 0);
    }

    public static DrawOp glow(int color, int cx, int cy, int r, int intensity) {
        return new DrawOp(GLOW, new int[]{cx, cy, r}, color, 0, intensity);
    }

    public static DrawOp grain(int... pts) {
        return new DrawOp(GRAIN, pts, 0, 0, 0);
    }
}
