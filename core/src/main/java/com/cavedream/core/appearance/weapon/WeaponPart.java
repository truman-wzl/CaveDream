package com.cavedream.core.appearance.weapon;

import java.util.ArrayList;
import java.util.List;

/** 武器部件（刃/护手/柄/首/宝石/弓弦/头）：一层，带 z 序、挂点 socket 与图元脚本。 */
public final class WeaponPart {

    public String name;         // blade|guard|grip|pommel|gem|string|head
    public int z;               // 绘制序（小=先画=更靠后层）
    public String socket;       // "hand"|"tip"|"base"|null —— 挂点语义
    public int socketX;         // 挂点在画布空间的局部坐标
    public int socketY;
    public Material mat;
    public List<DrawOp> ops = new ArrayList<>();

    public WeaponPart() {
    }

    public WeaponPart(String name, int z, Material mat, DrawOp... ops) {
        this.name = name;
        this.z = z;
        this.mat = mat;
        for (DrawOp o : ops) {
            this.ops.add(o);
        }
    }

    public WeaponPart socket(String kind, int x, int y) {
        this.socket = kind;
        this.socketX = x;
        this.socketY = y;
        return this;
    }
}
