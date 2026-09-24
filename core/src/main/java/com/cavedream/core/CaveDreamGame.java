package com.cavedream.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.cavedream.core.net.ServerConfig;
import com.cavedream.core.player.PlayerClass;
import com.cavedream.core.render.ItemCatalog;
import com.cavedream.core.save.GameSave;
import com.cavedream.core.screen.ClassScreen;
import com.cavedream.core.screen.LoadingScreen;
import com.cavedream.core.screen.SaveListScreen;
import com.cavedream.core.screen.SplashScreen;
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
        setScreen(new SplashScreen(this));   // 开场字标淡入淡出 → 主菜单
    }

    /** splash 结束后进入主菜单。 */
    public void showTitle() {
        setScreen(new TitleScreen(this));
    }

    /** 主菜单“继续游戏”→存档列表屏。 */
    public void showSaveList() {
        setScreen(new SaveListScreen(this));
    }

    @Override
    public void dispose() {
        super.dispose();
        com.cavedream.core.screen.CjkFonts.disposeAll();   // 释放跨屏共享的缓存字体
    }

    @Override
    public void render() {
        // F11 全屏/窗口切换（用 Graphics 接口，core 不依赖 LWJGL 后端）
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
            if (fullscreen) {
                Gdx.graphics.setWindowedMode(1600, 900);
            } else {
                Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
            fullscreen = !fullscreen;
        }
        super.render();
    }

    /** 标题选“新的梦”→先进选职业屏。 */
    public void startNewDream() {
        setScreen(new ClassScreen(this));
    }

    /** 选定职业后→生成加载屏（后台生成中世界，完成进 PlayScreen）；分配一个全新存档槽。 */
    public void startNewDream(PlayerClass playerClass) {
        setScreen(new LoadingScreen(this, playerClass, freshSeed(), null, freshSlot()));
    }

    private static long freshSeed() {
        return new java.util.Random().nextLong();
    }

    /** 游玩中按 ESC：退回标题界面（世界不保留；下次“继续游戏”从存档文件读回）。 */
    public void backToTitle() {
        setScreen(new TitleScreen(this));
    }

    /* ---------------- 本地存档（每账号可无限多档，每档一个文件） ---------------- */

    private static FileHandle saveFile(String slot) {
        return Gdx.files.external(".cavedream/" + slot);
    }

    /** 新存档分配唯一槽位文件名（时间戳+随机→不互盖、可无限）。 */
    private static String freshSlot() {
        return "save-" + System.currentTimeMillis() + "-" + (int) (Math.random() * 1000) + ".json";
    }

    /** 是否已有存档（驱动“继续游戏”可用性）。 */
    public boolean hasSave() {
        return !listSaves().isEmpty();
    }

    /** 列出本地已有存档（扫 .cavedream/save*.json，不限个数）。 */
    public java.util.List<GameSave> listSaves() {
        java.util.List<GameSave> out = new java.util.ArrayList<>();
        FileHandle dir = Gdx.files.external(".cavedream");
        if (!dir.exists()) {
            return out;
        }
        for (FileHandle f : dir.list()) {
            String n = f.name();
            if (n.startsWith("save") && n.endsWith(".json")) {
                try {
                    GameSave s = GameSave.fromJson(f.readString());
                    if (s.slot == null || s.slot.isEmpty()) {
                        s.slot = n;   // 旧档兼容：用文件名作槽位
                    }
                    out.add(s);
                } catch (Exception ignore) {
                    // 损坏存档跳过
                }
            }
        }
        return out;
    }

    /** 写存档到其所属槽位（slot 缺失则分配新槽）。 */
    public void saveGame(GameSave save) {
        if (save.slot == null || save.slot.isEmpty()) {
            save.slot = freshSlot();
        }
        FileHandle f = saveFile(save.slot);
        f.parent().mkdirs();
        f.writeString(save.toJson(), false);
        Gdx.app.log("CaveDream", "已存档 → " + f.name());
    }

    /** 继续最新一个存档（列表屏未用时的便捷入口）。 */
    public void continueGame() {
        java.util.List<GameSave> all = listSaves();
        if (all.isEmpty()) {
            Gdx.app.log("CaveDream", "无存档，转新游戏");
            startNewDream();
            return;
        }
        continueGame(all.get(all.size() - 1));
    }

    /** 继续指定存档：加载屏按存档种子重建中世界，进 PlayScreen 后套用改动/状态（沿用原槽）。 */
    public void continueGame(GameSave save) {
        PlayerClass pc = PlayerClass.valueOf(save.className);
        setScreen(new LoadingScreen(this, pc, save.seed, save, save.slot));
    }
}
