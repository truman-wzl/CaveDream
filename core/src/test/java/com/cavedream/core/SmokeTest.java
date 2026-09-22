package com.cavedream.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 骨架冒烟测试：验证 core 模块的测试链路（JUnit5 + AssertJ）可用。 */
class SmokeTest {

    @Test
    void dreamShouldBoot() {
        assertThat("CaveDream").startsWith("Cave");
    }
}
