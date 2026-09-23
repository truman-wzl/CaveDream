package com.cavedream.core.item;

/**
 * 材质（等价类基元）：木→石→铁→金→白金→圣。把"每一阶只差数值"的重复收敛成一张表——
 * 工具/物品的 power、命名、id 都由材质 + 类别派生，而非逐个手写常量。
 *
 * @param cn    中文材质名
 * @param power 挖掘力度倍率（木=1.0 初始不缩短，圣=10.0 即 −90%）
 */
public enum Material {
    WOOD("木", 1.0),
    STONE("石", 1.5),
    IRON("铁", 2.0),
    GOLD("金", 3.0),
    PLATINUM("白金", 4.5),
    SAINT("圣", 10.0);

    public final String cn;
    public final double power;

    Material(String cn, double power) {
        this.cn = cn;
        this.power = power;
    }

    /** 该材质工具在 id 命名空间中的序号（0..5）。 */
    public int tier() {
        return ordinal();
    }
}
