package com.cavedream.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.cavedream.core.CaveDreamGame;

/** 桌面启动器入口：创建 LWJGL3 窗口并挂载 core 游戏逻辑。 */
public final class Main {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CaveDream");
        // 窗口化启动：保留系统标题栏 + 最小化/最大化/关闭按钮（非无边框）；游戏内 F11 可切全屏
        // 1600×900：同 45 格竖视口下每格更多屏幕像素→更锐利（A：不动逻辑 16 格基座）
        config.setWindowedMode(1600, 900);
        config.setResizable(true);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new CaveDreamGame(), config);
    }

    private Main() {
    }
}
