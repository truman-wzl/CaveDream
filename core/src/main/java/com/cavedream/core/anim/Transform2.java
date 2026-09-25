package com.cavedream.core.anim;

/**
 * 2D 仿射变换（3×2 矩阵），纯数学、不依赖 libGDX，可单测。
 * 约定点变换：{@code (x,y) -> (a*x + c*y + e, b*x + d*y + f)}。
 * 列向量语义：{@link #mul(Transform2)} 计算 {@code this = this * other}（先应用 other，再应用 this）。
 */
public final class Transform2 {

    public float a = 1f, b = 0f, c = 0f, d = 1f, e = 0f, f = 0f;

    public Transform2() {
    }

    public Transform2 copy() {
        Transform2 t = new Transform2();
        t.a = a;
        t.b = b;
        t.c = c;
        t.d = d;
        t.e = e;
        t.f = f;
        return t;
    }

    public void setIdentity() {
        a = 1f;
        b = 0f;
        c = 0f;
        d = 1f;
        e = 0f;
        f = 0f;
    }

    public boolean isIdentity() {
        return a == 1f && b == 0f && c == 0f && d == 1f && e == 0f && f == 0f;
    }

    /** 平移 × 旋转(角度制) × 缩放 组合：局部 → 父空间。 */
    public void setTrs(float tx, float ty, float rotDeg, float sx, float sy) {
        double r = Math.toRadians(rotDeg);
        float cos = (float) Math.cos(r), sin = (float) Math.sin(r);
        a = cos * sx;
        b = sin * sx;
        c = -sin * sy;
        d = cos * sy;
        e = tx;
        f = ty;
    }

    /** {@code this = this * other}。 */
    public void mul(Transform2 o) {
        float na = a * o.a + c * o.b;
        float nb = b * o.a + d * o.b;
        float nc = a * o.c + c * o.d;
        float nd = b * o.c + d * o.d;
        float ne = a * o.e + c * o.f + e;
        float nf = b * o.e + d * o.f + f;
        a = na;
        b = nb;
        c = nc;
        d = nd;
        e = ne;
        f = nf;
    }

    public void set(Transform2 o) {
        a = o.a;
        b = o.b;
        c = o.c;
        d = o.d;
        e = o.e;
        f = o.f;
    }

    /** 返回本仿射矩阵的逆（det≈0 时退化为恒等）。蒙皮用 {@code world * invBind}。 */
    public Transform2 inverted() {
        float det = a * d - b * c;
        Transform2 r = new Transform2();
        if (Math.abs(det) < 1e-8f) {
            return r;   // 恒等
        }
        float idet = 1f / det;
        r.a = d * idet;
        r.b = -b * idet;
        r.c = -c * idet;
        r.d = a * idet;
        r.e = (c * f - d * e) * idet;
        r.f = (b * e - a * f) * idet;
        return r;
    }

    /** 变换一个点，结果写入 out（out 可与内部字段别名无关，直接返回数组）。 */
    public void apply(float x, float y, float[] out) {
        out[0] = a * x + c * y + e;
        out[1] = b * x + d * y + f;
    }

    public float ax(float x, float y) {
        return a * x + c * y + e;
    }

    public float ay(float x, float y) {
        return b * x + d * y + f;
    }

    public static Transform2 identity() {
        return new Transform2();
    }
}
