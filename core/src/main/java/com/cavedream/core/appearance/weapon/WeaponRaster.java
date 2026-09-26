package com.cavedream.core.appearance.weapon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 武器配方的确定性光栅化（纯逻辑、无 libGDX、可单测）：把 {@link WeaponDef} 解释成 ARGB 像素缓冲。
 * 支持整体合成 {@link #render} 与单部件分层 {@link #renderPart}。坐标 y 向下，画布 canvasW×canvasH。
 */
public final class WeaponRaster {

    private WeaponRaster() {
    }

    /** 所有部件按 z 叠成一张（图标用）。 */
    public static int[] render(WeaponDef def) {
        int w = def.canvasW, h = def.canvasH;
        int[] buf = new int[w * h];
        List<WeaponPart> ps = new ArrayList<>(def.parts);
        ps.sort(Comparator.comparingInt(p -> p.z));
        for (WeaponPart p : ps) {
            drawPart(buf, w, h, def, p);
        }
        return buf;
    }

    /** 只画指定部件（分层用，画布同尺寸、其余透明）。 */
    public static int[] renderPart(WeaponDef def, WeaponPart part) {
        int[] buf = new int[def.canvasW * def.canvasH];
        drawPart(buf, def.canvasW, def.canvasH, def, part);
        return buf;
    }

    private static void drawPart(int[] buf, int w, int h, WeaponDef def, WeaponPart p) {
        for (DrawOp op : p.ops) {
            drawOp(buf, w, h, def, p, op);
        }
    }

    private static void drawOp(int[] buf, int w, int h, WeaponDef def, WeaponPart p, DrawOp op) {
        int[] q = def.displayDeg != 0 ? rotatePts(op.pts, op.kind, def) : op.pts;
        switch (op.kind) {
            case DrawOp.POLY_FILL:
                fillPoly(buf, w, h, q, (x, y) -> fillColor(p, op, y, h));
                break;
            case DrawOp.POLY_STROKE:
                strokePoly(buf, w, h, q, Math.max(1, op.width), solid(strokeColor(p, op)));
                break;
            case DrawOp.LINE:
                thickLine(buf, w, h, q[0], q[1], q[2], q[3], Math.max(1, op.width),
                        solid(strokeColor(p, op)));
                break;
            case DrawOp.ARC:
                thickArc(buf, w, h, q[0], q[1], q[2], q[3], q[4],
                        Math.max(1, op.width), solid(strokeColor(p, op)));
                break;
            case DrawOp.CIRCLE:
                fillPoly(buf, w, h, circlePoly(q[0], q[1], q[2]), (x, y) -> fillColor(p, op, y, h));
                break;
            case DrawOp.GLOW:
                glow(buf, w, h, q[0], q[1], q[2], op.color, Math.max(1, op.intensity));
                break;
            case DrawOp.GRAIN:
                fillPoly(buf, w, h, q, (x, y) -> grainColor(def, p, x, y));
                break;
            default:
                break;
        }
    }

    /** 按 def.displayDeg 绕画布中心旋转图元坐标（ARC 附带角度偏移、CIRCLE/GLOW 只转心）。 */
    private static int[] rotatePts(int[] pts, byte kind, WeaponDef def) {
        double rad = Math.toRadians(def.displayDeg), cos = Math.cos(rad), sin = Math.sin(rad);
        double ox = def.canvasW / 2.0, oy = def.canvasH / 2.0;
        int[] r = pts.clone();
        if (kind == DrawOp.ARC) {
            double[] c = rot(pts[0], pts[1], ox, oy, cos, sin);
            r[0] = (int) Math.round(c[0]);
            r[1] = (int) Math.round(c[1]);
            r[3] = pts[3] + def.displayDeg;
            r[4] = pts[4] + def.displayDeg;
        } else if (kind == DrawOp.CIRCLE || kind == DrawOp.GLOW) {
            double[] c = rot(pts[0], pts[1], ox, oy, cos, sin);
            r[0] = (int) Math.round(c[0]);
            r[1] = (int) Math.round(c[1]);
        } else {
            for (int i = 0; i + 1 < r.length; i += 2) {
                double[] c = rot(pts[i], pts[i + 1], ox, oy, cos, sin);
                r[i] = (int) Math.round(c[0]);
                r[i + 1] = (int) Math.round(c[1]);
            }
        }
        return r;
    }

    private static double[] rot(double x, double y, double ox, double oy, double cos, double sin) {
        double dx = x - ox, dy = y - oy;
        return new double[]{ox + dx * cos - dy * sin, oy + dx * sin + dy * cos};
    }

    // ---- 取色 ----
    private static int fillColor(WeaponPart p, DrawOp op, int y, int h) {
        if (op.color != 0) {
            return 0xFF000000 | op.color;
        }
        Material m = p.mat;
        double t = h <= 1 ? 0 : y / (double) (h - 1);           // 顶亮底暗（金属竖向高光）
        double k = m.specular > 0 ? (1 - t) * (m.specular / 255.0) : 0;
        return 0xFF000000 | (mix(m.base, m.hi, k));
    }

    private static int strokeColor(WeaponPart p, DrawOp op) {
        if (op.color != 0) {
            return op.color;
        }
        return p.mat.edge;
    }

    private static int grainColor(WeaponDef def, WeaponPart p, int x, int y) {
        Material m = p.mat;
        double n = noise(x, y, def.seed, m.noise);
        int base = m.base;
        int c = n > 0.6 ? mix(base, m.hi, (n - 0.6) * 1.2) : (n < 0.35 ? mix(base, m.edge, (0.35 - n) * 1.4) : base);
        return 0xFF000000 | c;
    }

    // ---- 几何：多边形扫描填充 ----
    private interface PixelFn {
        int apply(int x, int y);
    }

    static void fillPoly(int[] buf, int w, int h, int[] pts, PixelFn colorAt) {
        int n = pts.length / 2;
        if (n < 3) {
            return;
        }
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            int y = pts[i * 2 + 1];
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        minY = Math.max(0, minY);
        maxY = Math.min(h - 1, maxY);
        for (int y = minY; y <= maxY; y++) {
            List<Integer> xs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int x0 = pts[i * 2], y0 = pts[i * 2 + 1];
                int x1 = pts[((i + 1) % n) * 2], y1 = pts[((i + 1) % n) * 2 + 1];
                if ((y0 <= y && y1 > y) || (y1 <= y && y0 > y)) {
                    double t = (y - (double) y0) / (y1 - y0);
                    xs.add((int) Math.round(x0 + t * (x1 - x0)));
                }
            }
            xs.sort(Integer::compareTo);
            for (int i = 0; i + 1 < xs.size(); i += 2) {
                int xa = Math.max(0, xs.get(i)), xb = Math.min(w - 1, xs.get(i + 1));
                for (int x = xa; x <= xb; x++) {
                    buf[y * w + x] = colorAt.apply(x, y);
                }
            }
        }
    }

    static void strokePoly(int[] buf, int w, int h, int[] pts, int width, int argb) {
        int n = pts.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            thickLine(buf, w, h, pts[i * 2], pts[i * 2 + 1], pts[j * 2], pts[j * 2 + 1], width, argb);
        }
    }

    static void thickLine(int[] buf, int w, int h, int x0, int y0, int x1, int y1, int width, int argb) {
        double dx = x1 - x0, dy = y1 - y0;
        double len = Math.hypot(dx, dy);
        int steps = (int) Math.max(1, Math.ceil(len));
        int rad = (width - 1) / 2;
        for (int s = 0; s <= steps; s++) {
            int cx = (int) Math.round(x0 + dx * s / steps);
            int cy = (int) Math.round(y0 + dy * s / steps);
            for (int oy = -rad; oy <= rad; oy++) {
                for (int ox = -rad; ox <= rad; ox++) {
                    set(buf, w, h, cx + ox, cy + oy, argb);
                }
            }
        }
    }

    static void thickArc(int[] buf, int w, int h, int cx, int cy, int r, int a0Deg, int a1Deg, int width, int argb) {
        double a0 = Math.toRadians(a0Deg), a1 = Math.toRadians(a1Deg);
        int steps = Math.max(8, (int) (Math.abs(a1 - a0) * r));
        int rad = (width - 1) / 2;
        for (int s = 0; s <= steps; s++) {
            double a = a0 + (a1 - a0) * s / steps;
            int px = (int) Math.round(cx + Math.cos(a) * r);
            int py = (int) Math.round(cy + Math.sin(a) * r);
            for (int oy = -rad; oy <= rad; oy++) {
                for (int ox = -rad; ox <= rad; ox++) {
                    set(buf, w, h, px + ox, py + oy, argb);
                }
            }
        }
    }

    static void glow(int[] buf, int w, int h, int cx, int cy, int r, int rgb, int intensity) {
        for (int y = Math.max(0, cy - r); y <= Math.min(h - 1, cy + r); y++) {
            for (int x = Math.max(0, cx - r); x <= Math.min(w - 1, cx + r); x++) {
                double d = Math.hypot(x - cx, y - cy);
                if (d > r) {
                    continue;
                }
                int a = (int) Math.round(intensity * (1 - d / r));
                blendOver(buf, w, x, y, ((a & 0xFF) << 24) | (rgb & 0xFFFFFF));
            }
        }
    }

    static int[] circlePoly(int cx, int cy, int r) {
        int seg = Math.max(8, r * 3);
        int[] pts = new int[seg * 2];
        for (int i = 0; i < seg; i++) {
            double a = i / (double) seg * Math.PI * 2;
            pts[i * 2] = (int) Math.round(cx + Math.cos(a) * r);
            pts[i * 2 + 1] = (int) Math.round(cy + Math.sin(a) * r);
        }
        return pts;
    }

    // ---- 像素/颜色工具 ----
    private static void set(int[] buf, int w, int h, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return;
        }
        buf[y * w + x] = argb;
    }

    private static void blendOver(int[] buf, int w, int x, int y, int src) {
        int dst = buf[y * w + x];
        int sa = (src >>> 24) & 0xFF;
        if (sa == 0) {
            return;
        }
        int da = (dst >>> 24) & 0xFF;
        int oa = sa + da * (255 - sa) / 255;
        if (oa == 0) {
            return;
        }
        int r = chan(src, 16) * sa + chan(dst, 16) * da * (255 - sa) / 255;
        int g = chan(src, 8) * sa + chan(dst, 8) * da * (255 - sa) / 255;
        int b = chan(src, 0) * sa + chan(dst, 0) * da * (255 - sa) / 255;
        buf[y * w + x] = (oa << 24) | ((r / oa & 0xFF) << 16) | ((g / oa & 0xFF) << 8) | (b / oa & 0xFF);
    }

    private static int chan(int argb, int shift) {
        return (argb >>> shift) & 0xFF;
    }

    private static int solid(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    static int mix(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** 值噪声（0..1），按种类给不同纹理：大马士革/木纹/附魔。 */
    static double noise(int x, int y, long seed, byte kind) {
        if (kind == Material.NOISE_NONE) {
            return 0.5;
        }
        double base = hash(x, y, seed);
        switch (kind) {
            case Material.NOISE_DAMASCUS:
                return 0.5 + 0.5 * Math.sin(x * 0.7 + base * 6.0 + y * 0.15);
            case Material.NOISE_WOOD:
                return 0.5 + 0.5 * Math.sin((y + base * 6) * 0.55);
            case Material.NOISE_ARCANE:
                return 0.5 + 0.5 * Math.sin((x + y) * 0.9 + base * 9.0);
            default:
                return base;
        }
    }

    static double hash(int x, int y, long seed) {
        long n = x * 374761393L + y * 668265263L + seed * 2654435761L;
        n = (n ^ (n >> 13)) * 1274126177L;
        return ((n ^ (n >> 16)) & 0xFF) / 255.0;
    }
}
