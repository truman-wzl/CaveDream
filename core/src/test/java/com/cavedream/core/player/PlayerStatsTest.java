package com.cavedream.core.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 双条资源与职业初始值测试。 */
class PlayerStatsTest {

    @Test
    void classSetsWeaponAndBars() {
        PlayerStats mage = new PlayerStats(PlayerClass.MAGE);
        assertThat(mage.maxLucidity()).isEqualTo(100);   // 全职业统一 100/10
        assertThat(mage.maxMana()).isEqualTo(10);
        assertThat(PlayerClass.WARRIOR.weapon().id()).isEqualTo(200);
    }

    @Test
    void damageReducesLucidityAndEntersCombat() {
        PlayerStats s = new PlayerStats(PlayerClass.WARRIOR);
        s.damage(30);
        assertThat(s.lucidity()).isEqualTo(70);   // 100-30
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
        s.damage(40);                       // lucidity 60, 进入战斗
        for (int i = 0; i < 60 * 10; i++) { // 推进 10 秒（脱战 3s 后开始回）
            s.update(1 / 60f);
        }
        assertThat(s.inCombat()).isFalse();
        assertThat(s.lucidity()).isGreaterThan(60);
    }

    @Test
    void manaRegensSlowerInCombat() {
        PlayerStats combat = new PlayerStats(PlayerClass.MAGE);   // maxMana 10
        PlayerStats idle = new PlayerStats(PlayerClass.MAGE);
        combat.spendMana(5);                // 降到 5（ratio²=0.25，回复可观测）
        idle.spendMana(5);
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
        assertThat(s.lucidity()).isEqualTo(50);   // 100 的一半
        assertThat(s.mana()).isEqualTo(s.maxMana());
    }
}
