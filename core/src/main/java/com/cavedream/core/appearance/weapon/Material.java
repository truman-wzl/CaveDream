package com.cavedream.core.appearance.weapon;

/** 部件材质（金属/木/附魔基调 + 噪声种类 + 高光强度）。op.color=0 时回退到这里取色。 */
public final class Material {

    /** 噪声种类：0 无、1 大马士革、2 木纹、3 附魔流光。 */
    public static final byte NOISE_NONE = 0, NOISE_DAMASCUS = 1, NOISE_WOOD = 2, NOISE_ARCANE = 3;

    public int base;        // 主体色 0xRRGGBB
    public int hi;          // 高光色
    public int edge;        // 描边/暗部色
    public byte noise;      // NOISE_*
    public int specular;    // 高光强度 0..255（金属强、木低）

    public Material() {
    }

    public Material(int base, int hi, int edge, byte noise, int specular) {
        this.base = base;
        this.hi = hi;
        this.edge = edge;
        this.noise = noise;
        this.specular = specular;
    }
}
