package com.cavedream.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.cavedream.core.net.ServerConfig;
import com.cavedream.core.render.ItemCatalog;
import com.cavedream.core.screen.PlayScreen;
import com.cavedream.core.screen.TitleScreen;

/**
 * 游戏主类（薄壳）：负责屏幕切换——登录（标题）界面 → L1 浅梦游玩界面。
 * 逻辑（world/dream 包）与表现（screen 包）分离，world/dream 全部可脱离窗口单测。
 */
public class CaveDreamGame extends Game {

    private boolean fullscreen = false;   // 默认窗口化启动（与 Main 一致）；F11 首次按下切全屏

    @Override
    public void create() {
        Gdx.app.log("CaveDream", "登录界面已就位");
        ItemCatalog.startAsync(ServerConfig.BASE_URL);   // 后台拉取 DB 物品图集，就绪前渲染回退程序生成
        setScreen(new TitleScreen(this));
    }

    @Override
    public void render() {
        // F11 全屏/窗口切换（用 Graphics 接口，core 不依赖 LWJGL 后端）
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
            if (fullscreen) {
                Gdx.graphics.setWindowedMode(1280, 720);
            } else {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
            fullscreen = !fullscreen;
        }
        super.render();
    }

    /** 标题界面选择"新的梦"：进入 L1（当前每次从新梦开始，M3 接存档后可"继续"）。 */
    public void startNewDream() {
        setScreen(new PlayScreen(this));
    }

    /** 游玩中按 ESC：退回标题界面（暂不保留世界，M3 存档系统接入后改为持久返回）。 */
    public void backToTitle() {
        setScreen(new TitleScreen(this));
    }
}
