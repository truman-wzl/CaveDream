package com.cavedream.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.cavedream.core.CaveDreamGame;

/** 桌面启动器入口：创建 LWJGL3 窗口并挂载 core 游戏逻辑。 */
public final class Main {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("CaveDream");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new CaveDreamGame(), config);
    }

    private Main() {
    }
}
