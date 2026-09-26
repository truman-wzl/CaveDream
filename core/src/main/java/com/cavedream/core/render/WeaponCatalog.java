package com.cavedream.core.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.cavedream.core.appearance.weapon.DrawOp;
import com.cavedream.core.appearance.weapon.WeaponDef;
import com.cavedream.core.appearance.weapon.WeaponLibrary;
import com.cavedream.core.appearance.weapon.WeaponPart;
import com.cavedream.core.item.Item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 武器配方目录：优先加载**烘焙进资源的 AI 配方**（classpath {@code weapons/<id>.json}），
 * 无则回退代码基线 {@link WeaponLibrary}。构建期 AI 设计器（我按你的描述生成 WeaponDef JSON）
 * 把结果写入 {@code core/src/main/resources/weapons/}，运行时此处读取、零网络、确定性。
 */
public final class WeaponCatalog {

    private static final Map<Integer, WeaponDef> loaded = new HashMap<>();
    private static final Set<Integer> missing = new HashSet<>();

    private WeaponCatalog() {
    }

    public static WeaponDef defFor(Item it) {
        return it == null ? null : defForId(it.id());
    }

    public static WeaponDef defForId(int id) {
        WeaponDef o = loaded.get(id);
        if (o != null) {
            return o;
        }
        if (!missing.contains(id)) {
            try {
                FileHandle fh = Gdx.files.classpath("weapons/" + id + ".json");
                if (fh.exists()) {
                    WeaponDef d = WeaponDef.fromJson(fh.readString());
                    validate(d);
                    loaded.put(id, d);
                    return d;
                }
                missing.add(id);
            } catch (Exception ignore) {
                missing.add(id);   // 无 GL/资源或损坏 → 回退基线
            }
        }
        return WeaponLibrary.defForId(id);
    }

    /** 轻量校验：坐标钳进画布、丢弃退化图元、部件材质兜底，防 AI 产出越界/畸形。 */
    static void validate(WeaponDef d) {
        if (d == null || d.parts == null) {
            return;
        }
        if (d.canvasW <= 0) {
            d.canvasW = 40;
        }
        if (d.canvasH <= 0) {
            d.canvasH = 40;
        }
        for (WeaponPart p : d.parts) {
            if (p.mat == null) {
                p.mat = new com.cavedream.core.appearance.weapon.Material(0xB0B0B0, 0xE0E0E0, 0x303030,
                        (byte) 0, 120);
            }
            if (p.ops != null) {
                p.ops.removeIf(WeaponCatalog::degenerate);
            }
        }
    }

    private static boolean degenerate(DrawOp op) {
        return op == null || op.pts == null || op.pts.length < 2;
    }
}
