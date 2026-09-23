package com.cavedream.core.light;

/**
 * 游戏时钟与昼夜天光（GDD 光照系统）。
 * 节奏：1 真实分钟 = 1 游戏小时 ⇒ 1 真实秒 = 1 游戏分钟；24 真实分钟 = 一昼夜（1440 游戏分）。
 * 天光分 16 级（1 最暗=全黑 … 16 最亮=正午），由时刻的分段曲线决定，供 {@link LightEngine} 缩放天光。
 */
public final class GameClock {

    /** 内部亮度用 0~15（对外 1~16 级 = 内部值 +1）。 */
    public static final int MAX_LEVEL = 15;

    // 时刻锚点：{小时, 白昼系数 0~1}，线性插值；深夜=0（全黑靠角色光/光源），6:00 天刚亮≈ 0.35
    private static final double[][] CURVE = {
            {0, 0.0}, {5, 0.15}, {6, 0.5}, {8, 0.85}, {11, 0.95}, {12, 1.0},
            {15, 0.95}, {18, 0.6}, {20, 0.2}, {21, 0.02}, {24, 0.0},
    };

    private double gameMinutes;   // 0 ~ 1440

    public GameClock() {
        this(6 * 60);   // 开局 6:00 清晨（天刚亮）
    }

    public GameClock(double startMinutes) {
        this.gameMinutes = startMinutes;
    }

    /** 推进：realSeconds 为真实秒。1 真实秒 = 1 游戏分钟。 */
    public void update(float realSeconds) {
        gameMinutes += realSeconds;
        gameMinutes %= 1440.0;
        if (gameMinutes < 0) {
            gameMinutes += 1440.0;
        }
    }

    public double hour() {
        return gameMinutes / 60.0;
    }

    public int hourOfDay() {
        return (int) (gameMinutes / 60.0) % 24;
    }

    public int minuteOfHour() {
        return (int) Math.floor(gameMinutes) % 60;
    }

    /** 白昼系数 0~1（对当前时刻插值）。 */
    public double daylightFactor() {
        double h = hour();
        for (int i = 0; i < CURVE.length - 1; i++) {
            double h0 = CURVE[i][0], f0 = CURVE[i][1];
            double h1 = CURVE[i + 1][0], f1 = CURVE[i + 1][1];
            if (h >= h0 && h <= h1) {
                double t = (h1 == h0) ? 0 : (h - h0) / (h1 - h0);
                return f0 + (f1 - f0) * t;
            }
        }
        return 0.0;
    }

    /** 天光内部等级 0~15（正午 15，午夜 0）。 */
    public int daylightLevel0to15() {
        return clamp((int) Math.round(daylightFactor() * MAX_LEVEL), 0, MAX_LEVEL);
    }

    /** 天光对外 1~16 级。 */
    public int skyLevel1to16() {
        return daylightLevel0to15() + 1;
    }

    /**
     * 指向太阳的单位向量 {x,y}，与 SkyRenderer 的太阳屏幕位置一致（太阳从画面左侧升起）：
     * 6点→左上(-1,0)、12点→天顶(0,1)、18点→右上(+1,0)；夜间 y 分量为负→ {@link SunShadow} 不投影。
     * 注意 x 取 -cos：清晨太阳在画面左，阴影才拖向右侧（背光侧）。
     */
    public float[] sunDirection() {
        double theta = (hour() - 6) / 12.0 * Math.PI;
        return new float[]{(float) -Math.cos(theta), (float) Math.sin(theta)};
    }

    /** 是否夜间（刷怪高系数窗口）：18:30–次日 5:59。 */
    public boolean isNight() {
        double h = hour();
        return h >= 18.5 || h < 6.0;
    }

    /** HH:MM 文本，供 HUD。 */
    public String format() {
        return String.format("%02d:%02d", hourOfDay(), minuteOfHour());
    }

    /** 当前总分钟（自 0:00，0~1439），供存档。 */
    public int totalMinutes() {
        return (int) Math.floor(gameMinutes);
    }

    /** 存档恢复时刻。 */
    public void setTotalMinutes(int minutes) {
        gameMinutes = Math.floorMod(minutes, 1440);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
