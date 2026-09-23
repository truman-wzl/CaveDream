package com.cavedream.core.light;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 昼夜时钟与天光等级测试。 */
class GameClockTest {

    @Test
    void noonIsBrightestMidnightDarkest() {
        GameClock noon = new GameClock(12 * 60);
        GameClock midnight = new GameClock(0);
        assertThat(noon.skyLevel1to16()).isEqualTo(16);       // 正午最亮=16 级
        assertThat(midnight.skyLevel1to16()).isEqualTo(1);    // 午夜全黑=1 级
    }

    @Test
    void oneRealMinuteAdvancesOneGameHour() {
        GameClock clock = new GameClock(0);
        clock.update(60f);                 // 60 真实秒 = 1 真实分 = 1 游戏时
        assertThat(clock.hourOfDay()).isEqualTo(1);
        clock.update(60f * 23);            // 再过 23 分钟 → 满一昼夜回到 0
        assertThat(clock.hourOfDay()).isZero();
    }

    @Test
    void levelMonotonicAcrossMorning() {
        GameClock dawn = new GameClock(6 * 60);
        GameClock morning = new GameClock(9 * 60);
        assertThat(morning.daylightLevel0to15())
                .isGreaterThan(dawn.daylightLevel0to15());
    }

    @Test
    void formatShowsHourMinute() {
        assertThat(new GameClock(13 * 60 + 30).format()).isEqualTo("13:30");
    }
}
