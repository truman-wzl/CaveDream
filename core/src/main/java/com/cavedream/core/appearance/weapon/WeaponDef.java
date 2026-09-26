package com.cavedream.core.appearance.weapon;

import com.badlogic.gdx.utils.Json;

import java.util.ArrayList;
import java.util.List;

/**
 * 武器绘制配方（一切皆 ID）：一件武器的全部部件 + 画布尺寸 + 种子。可 JSON 序列化，供
 * 构建期 AI 生成/烘焙、运行时确定性解释渲染（{@code WeaponRaster}/{@code WeaponRenderer}）。
 */
public final class WeaponDef {

    public static final int VERSION = 1;

    public String name;
    public int canvasW = 40, canvasH = 40;
    public long seed;
    public int defVersion = VERSION;
    public int displayDeg;      // 图标/手持显示时整体旋转角（度）：区分职业（战士斜持 45°、刺客竖直 0°）
    public List<WeaponPart> parts = new ArrayList<>();

    public WeaponDef() {
    }

    public WeaponDef(String name, int canvasW, int canvasH, long seed, WeaponPart... parts) {
        this.name = name;
        this.canvasW = canvasW;
        this.canvasH = canvasH;
        this.seed = seed;
        for (WeaponPart p : parts) {
            this.parts.add(p);
        }
    }

    public WeaponPart part(String name) {
        for (WeaponPart p : parts) {
            if (p.name.equals(name)) {
                return p;
            }
        }
        return null;
    }

    public WeaponPart socketPart(String socket) {
        for (WeaponPart p : parts) {
            if (socket.equals(p.socket)) {
                return p;
            }
        }
        return null;
    }

    public String toJson() {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        return json.prettyPrint(this);
    }

    public static WeaponDef fromJson(String s) {
        Json json = new Json();
        json.setIgnoreUnknownFields(true);
        return json.fromJson(WeaponDef.class, s);
    }
}
