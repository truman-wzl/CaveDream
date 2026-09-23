package com.cavedream.core.save;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 存档 JSON 往返测试（序列化保真）。 */
class GameSaveTest {

    @Test
    void roundTripsAllFields() {
        GameSave s = new GameSave();
        s.className = "MAGE";
        s.seed = 123;
        s.editIdx = new int[]{5, 100, 200};
        s.editBlock = new int[]{0, 3, 1};
        s.playerX = 321.5f;
        s.playerY = 96.25f;
        s.invItem = new int[]{-1, 100, 3};
        s.invCount = new int[]{0, 1, 30};
        s.invSelected = 2;
        s.lucidity = 55;
        s.maxLucidity = 90;
        s.mana = 7;
        s.maxMana = 20;
        s.clockMinutes = 738;
        s.coins = 42;

        GameSave r = GameSave.fromJson(s.toJson());
        assertThat(r.className).isEqualTo("MAGE");
        assertThat(r.editIdx).containsExactly(5, 100, 200);
        assertThat(r.editBlock).containsExactly(0, 3, 1);
        assertThat(r.playerX).isEqualTo(321.5f);
        assertThat(r.invCount).containsExactly(0, 1, 30);
        assertThat(r.lucidity).isEqualTo(55);
        assertThat(r.clockMinutes).isEqualTo(738);
        assertThat(r.coins).isEqualTo(42);
    }
}
