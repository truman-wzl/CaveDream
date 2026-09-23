package com.cavedream.core.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 双条资源与职业初始值测试。 */
class PlayerStatsTest {

    @Test
    void classSetsWeaponAndBars() {
        PlayerStats mage = new PlayerStats(PlayerClass.MAGE);
        assertThat(mage.maxLucidity()).isEqualTo(90);
        assertThat(mage.maxMana()).isEqualTo(20);
        assertThat(PlayerClass.WARRIOR.weapon().id()).isEqualTo(200);
    }

    @Test
    void damageReducesLucidityAndEntersCombat() {
        PlayerStats s = new PlayerStats(PlayerClass.WARRIOR);
        s.damage(30);
        assertThat(s.lucidity()).isEqualTo(90);
        assertThat(s.inCombat()).isTrue();
    }

    @Test
    void zeroLucidityTriggersDreamBreak() {
        PlayerStats s = new PlayerStats(PlayerClass.WARRIOR);
        s.damage(999);
        assertThat(s.lucidity()).isZero();
        assertThat(s.isDreamBreak()).isTrue();
    }

    @Test
    void outOfCombatRegensLucidity() {
        PlayerStats s = new PlayerStats(PlayerClass.WARRIOR);
        s.damage(60);                       // lucidity 60, 进入战斗
        for (int i = 0; i < 60 * 10; i++) { // 推进 10 秒（脱战 3s 后开始回）
            s.update(1 / 60f);
        }
        assertThat(s.inCombat()).isFalse();
        assertThat(s.lucidity()).isGreaterThan(60);
    }

    @Test
    void manaRegensSlowerInCombat() {
        PlayerStats combat = new PlayerStats(PlayerClass.MAGE);   // maxMana 20
        PlayerStats idle = new PlayerStats(PlayerClass.MAGE);
        combat.spendMana(10);               // 降到 10（ratio²=0.25，回复可观测）
        idle.spendMana(10);
        combat.damage(1);                    // combat 进入战斗态（魔能 25% 回复）
        for (int i = 0; i < 120; i++) {      // 2 秒（combat 仍 <3s 保持战斗）
            combat.update(1 / 60f);
            idle.update(1 / 60f);
        }
        assertThat(idle.mana()).isGreaterThan(combat.mana());
    }

    @Test
    void spendManaFailsWhenInsufficient() {
        PlayerStats s = new PlayerStats(PlayerClass.WARRIOR);   // maxMana 10
        assertThat(s.spendMana(11)).isFalse();
        assertThat(s.mana()).isEqualTo(10);
        assertThat(s.spendMana(4)).isTrue();
        assertThat(s.mana()).isEqualTo(6);
    }

    @Test
    void reviveAtAnchorRestores() {
        PlayerStats s = new PlayerStats(PlayerClass.WARRIOR);
        s.damage(999);
        s.reviveAtAnchor();
        assertThat(s.isDreamBreak()).isFalse();
        assertThat(s.lucidity()).isEqualTo(60);   // 120 的一半
        assertThat(s.mana()).isEqualTo(s.maxMana());
    }
}
