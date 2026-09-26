package com.cavedream.core.appearance.weapon;

import com.cavedream.core.item.Item;

/**
 * 武器基线配方库（一切皆 ID）：按 item id 给每件武器一份**专属** {@link WeaponDef}（分部件、竖直构图，
 * 尾在下、刃朝上，40×40 画布）。运行时由 {@code WeaponRenderer} 确定性渲染。构建期 AI 设计器可覆盖基线。
 */
public final class WeaponLibrary {

    private static final int STEEL = 0xB8BCC6, STEEL_HI = 0xF2F5FA, STEEL_EDGE = 0x3A3F4A;
    private static final int GOLD = 0xC9A94A, GOLD_HI = 0xF0D878, GOLD_EDGE = 0x6A5018;
    private static final int WOOD = 0x8A5A2B, WOOD_HI = 0xA8703A, WOOD_EDGE = 0x3A2412;
    private static final int MAGE_GEM = 0x8E7CC3, SUM_GEM = 0x4FA0FF;

    private static final Material STEEL_M = new Material(STEEL, STEEL_HI, STEEL_EDGE, Material.NOISE_DAMASCUS, 200);
    private static final Material GOLD_M = new Material(GOLD, GOLD_HI, GOLD_EDGE, Material.NOISE_NONE, 170);
    private static final Material WOOD_M = new Material(WOOD, WOOD_HI, WOOD_EDGE, Material.NOISE_WOOD, 40);
    private static final Material WOOD_BLADE_M = new Material(0x9A6A38, 0xC89458, 0x4A2E18, Material.NOISE_WOOD, 90);

    private WeaponLibrary() {
    }

    /** 按物品给配方；无匹配返回 null（回退旧图标）。 */
    public static WeaponDef defFor(Item it) {
        return it == null ? null : defForId(it.id());
    }

    /** 按 item id 给配方。 */
    public static WeaponDef defForId(int id) {
        if (id == 200) {
            return sword();
        }
        if (id == 201) {
            return wand();
        }
        if (id == 202) {
            return staff();
        }
        if (id == 203) {
            return bow();
        }
        if (id == 204) {
            return dagger();
        }
        if (id >= 100 && id <= 105) {
            return pickaxe();
        }
        if (id >= 110 && id <= 115) {
            return axe();
        }
        return null;
    }

    private static WeaponPart part(String name, int z, Material m, DrawOp... ops) {
        return new WeaponPart(name, z, m, ops);
    }

    private static WeaponDef sword() {
        WeaponPart blade = part("blade", 1, WOOD_BLADE_M,
                DrawOp.polyFill(0, 24, 1, 28, 8, 28, 31, 20, 31, 20, 8),
                DrawOp.grain(24, 1, 28, 8, 28, 31, 20, 31, 20, 8),
                DrawOp.line(0xC89458, 1, 22, 5, 22, 30)).socket("tip", 24, 1);
        WeaponPart guard = part("guard", 2, WOOD_M,
                DrawOp.polyFill(0, 15, 31, 33, 31, 33, 34, 15, 34));
        WeaponPart grip = part("grip", 3, WOOD_M,
                DrawOp.line(0, 4, 24, 34, 24, 45)).socket("hand", 24, 41);
        WeaponPart pommel = part("pommel", 4, WOOD_M, DrawOp.circle(0, 24, 46, 2));
        WeaponDef d = new WeaponDef("sword", 48, 48, 2001L, blade, guard, grip, pommel);
        // 不烘 displayDeg：斜 45° 由绘制端（rest 叠加 batch 逆时针旋转）统一处理，避免两套旋转系打架
        return d;
    }

    private static WeaponDef dagger() {
        WeaponPart blade = part("blade", 1, STEEL_M,
                DrawOp.polyFill(0, 20, 12, 23, 16, 23, 25, 17, 25, 17, 16),
                DrawOp.grain(20, 12, 23, 16, 23, 25, 17, 25, 17, 16)).socket("tip", 20, 12);
        WeaponPart guard = part("guard", 2, GOLD_M, DrawOp.polyFill(0, 15, 25, 25, 25, 25, 27, 15, 27));
        WeaponPart grip = part("grip", 3, WOOD_M, DrawOp.line(0, 3, 20, 27, 20, 35)).socket("hand", 20, 31);
        WeaponPart pommel = part("pommel", 4, GOLD_M, DrawOp.circle(0, 20, 36, 2));
        return new WeaponDef("dagger", 40, 40, 2004L, blade, guard, grip, pommel);
    }

    private static WeaponDef wand() {
        WeaponPart shaft = part("grip", 1, WOOD_M, DrawOp.line(0, 3, 20, 14, 20, 36)).socket("hand", 20, 30);
        WeaponPart collar = part("guard", 2, GOLD_M, DrawOp.polyFill(0, 16, 12, 24, 12, 23, 15, 17, 15));
        WeaponPart gem = part("gem", 3, new Material(MAGE_GEM, 0xC8B0F0, 0x4A3670, Material.NOISE_ARCANE, 150),
                DrawOp.circle(0, 20, 9, 4), DrawOp.glow(MAGE_GEM, 20, 9, 8, 150)).socket("tip", 20, 5);
        return new WeaponDef("wand", 40, 40, 2002L, shaft, collar, gem);
    }

    private static WeaponDef staff() {
        WeaponPart shaft = part("grip", 1, WOOD_M, DrawOp.line(0, 3, 20, 12, 20, 37)).socket("hand", 20, 28);
        WeaponPart ring = part("guard", 2, GOLD_M, DrawOp.arc(0, 2, 20, 13, 5, 0, 360));
        WeaponPart head = part("gem", 3, new Material(SUM_GEM, 0xBFE6FF, 0x2E6E9E, Material.NOISE_ARCANE, 180),
                DrawOp.polyFill(0, 20, 3, 25, 10, 20, 17, 15, 10), DrawOp.glow(SUM_GEM, 20, 10, 9, 170)).socket("tip", 20, 3);
        return new WeaponDef("staff", 40, 40, 2003L, shaft, ring, head);
    }

    private static WeaponDef bow() {
        WeaponPart limb = part("blade", 1, WOOD_M,
                DrawOp.arc(0, 3, 6, 20, 15, -75, 75)).socket("tip", 10, 6);
        WeaponPart string = part("string", 2, new Material(0xDDDDB0, 0xFFFFE0, 0x8A8A70, Material.NOISE_NONE, 0),
                DrawOp.line(0, 1, 10, 6, 10, 34));
        WeaponPart grip = part("grip", 3, GOLD_M, DrawOp.line(0, 3, 20, 18, 20, 22)).socket("hand", 19, 20);
        return new WeaponDef("bow", 40, 40, 2005L, limb, string, grip);
    }

    private static WeaponDef pickaxe() {
        WeaponPart handle = part("grip", 1, WOOD_M, DrawOp.line(0, 3, 20, 12, 20, 37)).socket("hand", 20, 30);
        WeaponPart head = part("head", 2, STEEL_M,
                DrawOp.polyFill(0, 6, 11, 20, 6, 34, 11, 33, 14, 20, 10, 7, 14),
                DrawOp.grain(6, 11, 20, 6, 34, 11, 33, 14, 20, 10, 7, 14)).socket("tip", 34, 11);
        return new WeaponDef("pickaxe", 40, 40, 1001L, handle, head);
    }

    private static WeaponDef axe() {
        WeaponPart handle = part("grip", 1, WOOD_M, DrawOp.line(0, 3, 20, 8, 20, 37)).socket("hand", 20, 30);
        WeaponPart head = part("head", 2, STEEL_M,
                DrawOp.polyFill(0, 20, 7, 31, 9, 33, 16, 29, 21, 20, 16),
                DrawOp.grain(20, 7, 31, 9, 33, 16, 29, 21, 20, 16)).socket("tip", 33, 15);
        return new WeaponDef("axe", 40, 40, 1101L, handle, head);
    }
}
