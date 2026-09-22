package com.cavedream.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.cavedream.core.CaveDreamGame;

/** 桌面启动器入口：创建 LWJGL3 窗口并挂载 core 游戏逻辑。 */
public final class Main {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CaveDream");
        // 启动即全屏（桌面原生分辨率）；游戏内 F11 可切回窗口模式
        config.setFullscreenMode(Lwjgl3ApplicationConfiguration.getDisplayMode());
        config.setWindowedMode(1280, 720);   // F11 切回窗口时的尺寸
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new CaveDreamGame(), config);
    }

    private Main() {
    }
}
