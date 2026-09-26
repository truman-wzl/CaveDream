package com.cavedream.core.appearance.weapon;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** 武器光栅化（纯逻辑）：确定性、非空、分层隔离、JSON 往返。 */
class WeaponRasterTest {

    private static WeaponDef sample() {
        Material steel = new Material(0xB8BCC6, 0xF0F3F8, 0x3A3F4A, Material.NOISE_DAMASCUS, 200);
        Material wood = new Material(0x8A5A2B, 0xA8703A, 0x3A2412, Material.NOISE_WOOD, 40);
        WeaponPart blade = new WeaponPart("blade", 1, steel,
                DrawOp.polyFill(0, 20, 4, 24, 10, 24, 26, 16, 26, 16, 20, 4),   // 竖直刃
                DrawOp.grain(20, 4, 24, 10, 24, 26, 16, 26, 16, 20, 4));
        blade.socket("tip", 20, 4);
        WeaponPart grip = new WeaponPart("grip", 2, wood,
                DrawOp.line(0, 3, 20, 28, 20, 36));
        grip.socket("hand", 20, 32);
        WeaponPart gem = new WeaponPart("gem", 3, new Material(0x8E7CC3, 0xC8B0F0, 0x4A3670, Material.NOISE_ARCANE, 120),
                DrawOp.circle(0, 20, 26, 2), DrawOp.glow(0x8E7CC3, 20, 26, 5, 120));
        return new WeaponDef("sample", 40, 40, 12345L, blade, grip, gem);
    }

    private static int opaqueCount(int[] buf) {
        int n = 0;
        for (int v : buf) {
            if ((v & 0xFFFFFF) != 0 && (v >>> 24) != 0) {
                n++;
            }
        }
        return n;
    }

    @Test
    void renderIsDeterministicAndNonEmpty() {
        WeaponDef d = sample();
        int[] a = WeaponRaster.render(d);
        int[] b = WeaponRaster.render(d);
        assertThat(a).containsExactly(b);
        assertThat(opaqueCount(a)).as("应画出可见像素").isGreaterThan(40);
    }

    @Test
    void renderPartIsolatesLayer() {
        WeaponDef d = sample();
        int[] blade = WeaponRaster.renderPart(d, d.part("blade"));
        int[] gem = WeaponRaster.renderPart(d, d.part("gem"));
        assertThat(opaqueCount(blade)).isGreaterThan(10);
        assertThat(opaqueCount(gem)).isGreaterThan(2);
        assertThat(blade).isNotEqualTo(gem);
    }

    @Test
    void socketsPresent() {
        WeaponDef d = sample();
        assertThat(d.socketPart("hand")).isNotNull();
        assertThat(d.socketPart("tip")).isNotNull();
    }

    @Test
    void jsonRoundTrips() {
        WeaponDef d = sample();
        WeaponDef r = WeaponDef.fromJson(d.toJson());
        assertThat(r.canvasW).isEqualTo(40);
        assertThat(r.parts).hasSize(3);
        assertThat(r.part("blade").ops.get(0).pts).containsExactly(d.part("blade").ops.get(0).pts);
        assertThat(Arrays.equals(WeaponRaster.render(r), WeaponRaster.render(d))).isTrue();
    }
}
