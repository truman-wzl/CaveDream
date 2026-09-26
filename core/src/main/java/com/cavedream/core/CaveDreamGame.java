package com.cavedream.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.cavedream.core.net.CloudSaveClient;
import com.cavedream.core.net.NpcDialogueProvider;
import com.cavedream.core.net.ServerConfig;
import com.cavedream.core.player.PlayerClass;
import com.cavedream.core.render.ItemCatalog;
import com.cavedream.core.render.PaintedLook;
import com.cavedream.core.appearance.LookSpec;
import com.cavedream.core.appearance.PixelLookForge;
import com.cavedream.core.save.GameSave;
import com.cavedream.core.screen.ClassScreen;
import com.cavedream.core.screen.LoadingScreen;
import com.cavedream.core.screen.LookStudioScreen;
import com.cavedream.core.screen.PaintStudioScreen;
import com.cavedream.core.screen.PlayScreen;
import com.cavedream.core.screen.SaveListScreen;
import com.cavedream.core.screen.SplashScreen;
import com.cavedream.core.screen.TitleScreen;

/**
 * 游戏主类（薄壳）：负责屏幕切换——登录（标题）界面 → L1 浅梦游玩界面。
 * 逻辑（world/dream 包）与表现（screen 包）分离，world/dream 全部可脱离窗口单测。
 */
public class CaveDreamGame extends Game {

    private boolean fullscreen = false;   // 默认窗口化启动（与 Main 一致）；F11 首次按下切全屏

    /** 云存档：会话 token（登录写入 session.json、重启自动读回）+ 版本 + 客户端。 */
    public static final String GAME_VERSION = "0.53";
    private String sessionToken;
    private String sessionNick;
    private volatile String lastCloudMsg;                                 // 上次云存档结果（主菜单回显）
    private final CloudSaveClient cloudSaves = new CloudSaveClient(ServerConfig.BASE_URL);
    private final NpcDialogueProvider npcDialogue = new NpcDialogueProvider();   // NPC 对话（大模型，读环境变量 key）
    private PlayScreen currentPlay;                                              // 活跃世界屏（供法典画板“重绘后返回”）

    public NpcDialogueProvider npcDialogue() {
        return npcDialogue;
    }

    @Override
    public void create() {
        Gdx.app.log("CaveDream", "登录界面已就位");
        ItemCatalog.startAsync(ServerConfig.BASE_URL);   // 后台拉取 DB 物品图集，就绪前渲染回退程序生成
        loadSession();                                   // 读回上次登录的 token（供云存档）
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

    /** 选定职业后→先命名存档（创建时命名）→再进外观骰子屏。 */
    public void nameNewDream(PlayerClass playerClass) {
        setScreen(new com.cavedream.core.screen.NameInputScreen(this, "为你的梦命名",
                playerClass.cn() + "之梦",
                name -> setScreen(new LookStudioScreen(this, playerClass, name)),
                this::showTitle));
    }

    /** 法典画板：以 initial 为起点打开重绘画板，完成回调 onDone 后返回世界。 */
    public void openPaint(PaintedLook initial, String title, java.util.function.Consumer<PaintedLook> onDone) {
        setScreen(new PaintStudioScreen(this, initial, title, onDone));
    }

    /** 重绘完成/放弃→切回之前的世界屏（PlayScreen 未 dispose，纹理/状态仍在）。 */
    public void resumeFromPaint() {
        if (currentPlay != null) {
            setScreen(currentPlay);
        }
    }

    /** PlayScreen 在 show 时登记自己为当前世界屏。 */
    public void setCurrentPlay(PlayScreen ps) {
        this.currentPlay = ps;
    }

    /** 旧建号入口（手涂外观）：已由骰子屏取代，保留供 PaintStudioScreen 回退。 */
    public void startNewDream(PlayerClass playerClass, String name, PaintedLook appearance) {
        setScreen(new LoadingScreen(this, playerClass, freshSeed(), null, freshSlot(), appearance, name, null));
    }

    /** 外观骰子确认→锻造像素→生成加载屏；分配全新存档槽，并携带蓝图供存档复现。 */
    public void startNewDream(PlayerClass playerClass, String name, LookSpec spec) {
        PaintedLook appearance = PixelLookForge.forge(spec);
        setScreen(new LoadingScreen(this, playerClass, freshSeed(), null, freshSlot(), appearance, name, spec));
    }

    /** 存档列表改名：写显示名→落本地→同步云端（slot 不变、仅 save_name 变）→回列表。 */
    public void renameSave(GameSave save, String name) {
        save.saveName = name;
        saveGame(save);
        if (sessionToken != null) {
            uploadSave(save);
        }
        showSaveList();
    }

    /** 弹命名屏为某存档改名（列表“改名”按钮）。 */
    public void promptRename(GameSave save) {
        String def = (save.saveName == null || save.saveName.isBlank()) ? save.className : save.saveName;
        setScreen(new com.cavedream.core.screen.NameInputScreen(this, "重新命名存档", def,
                name -> renameSave(save, name), this::showSaveList));
    }

    private static long freshSeed() {
        return new java.util.Random().nextLong();
    }

    /** 游玩中按 ESC：退回标题界面（世界不保留；下次“继续游戏”从存档文件读回）。 */
    public void backToTitle() {
        currentPlay = null;
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

    /** 删除某槽位的本地存档（存档列表里删档；“一切皆文件”的本地 CRUD-Delete）。 */
    public void deleteSave(String slot) {
        if (slot == null || slot.isEmpty()) {
            return;
        }
        saveFile(slot).delete();
        Gdx.app.log("CaveDream", "已删除存档 " + slot);
    }

    /* ---------------- 云存档（本地权威 + 云端备份，登录后可用） ---------------- */

    /** 登录成功写入会话（存 session.json + 内存，供 PlayScreen 云存档用）。 */
    public void setSession(String token, String nickname) {
        this.sessionToken = token;
        this.sessionNick = nickname;
        String t = token == null ? "" : token;
        String n = nickname == null ? "" : nickname;
        try {
            Gdx.files.external("cavedream/session.json")
                    .writeString("{\"token\":\"" + t + "\",\"nickname\":\"" + n + "\"}", false);
        } catch (Exception ignore) {
            // 写会话失败不影响本地游戏
        }
    }

    public String sessionToken() {
        return sessionToken;
    }

    public String sessionNick() {
        return sessionNick;
    }

    private void loadSession() {
        try {
            FileHandle fh = Gdx.files.external("cavedream/session.json");
            if (fh.exists()) {
                String body = fh.readString();
                sessionToken = between(body, "\"token\":\"");
                sessionNick = between(body, "\"nickname\":\"");
            }
        } catch (Exception ignore) {
            // 会话损坏当作未登录
        }
    }

    private static String between(String s, String key) {
        int a = s.indexOf(key);
        if (a < 0) {
            return null;
        }
        int start = a + key.length();
        int end = s.indexOf('"', start);
        return end > start ? s.substring(start, end) : null;
    }

    /** 退出时同步上传云端（write-back）。返回结果文案；同时存入 lastCloudMsg 供主菜单回显。 */
    public String uploadSave(GameSave save) {
        String msg;
        if (sessionToken == null) {
            msg = "未登录，云端跳过";
        } else if (save.slot == null || save.slot.isEmpty()) {
            msg = "存档无槽名，跳过";
        } else {
            String json = save.toJson();
            String name = (save.saveName != null && !save.saveName.isBlank())
                    ? save.saveName : (save.className + " · " + save.coins + "币");
            String err = cloudSaves.upload(sessionToken, save.slot, name, save.seed, GAME_VERSION, json);
            msg = err == null ? "云端已同步（" + save.slot + "）" : ("云端同步失败：" + err);
        }
        lastCloudMsg = msg;
        Gdx.app.log("CaveDream", "云存档：" + msg);
        return msg;
    }

    /** 取走并清空上次云存档结果消息（主菜单弹出）。 */
    public String takeCloudMsg() {
        String m = lastCloudMsg;
        lastCloudMsg = null;
        return m;
    }

    /** 云端槽位摘要（阻塞，需在后台线程调）；未登录返回空。 */
    public java.util.List<java.util.Map<String, Object>> cloudList() {
        return sessionToken == null ? java.util.List.of() : cloudSaves.list(sessionToken);
    }

    /** read-through：从云端拉某槽→写回本地主存→返回 GameSave（失败 null）。阻塞，后台线程调。 */
    public GameSave cloudPull(String slotKey) {
        if (sessionToken == null) {
            return null;
        }
        String json = cloudSaves.download(sessionToken, slotKey);
        if (json == null) {
            return null;
        }
        try {
            GameSave s = GameSave.fromJson(json);
            s.slot = slotKey;
            saveGame(s);                     // 落到本地缓存，下次即本地命中
            return s;
        } catch (Exception e) {
            return null;
        }
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
        PlayerClass pc;
        try {
            pc = PlayerClass.valueOf(save.className);   // 旧档/损坏职业名→兜底（装备仍从背包恢复）
        } catch (Exception e) {
            pc = PlayerClass.WARRIOR;
        }
        PaintedLook look = new PaintedLook();
        if (save.faceColors != null && save.faceColors.length == PaintedLook.W * PaintedLook.H) {
            look = new PaintedLook(save.faceTemplateId, save.faceColors);
        }
        setScreen(new LoadingScreen(this, pc, save.seed, save, save.slot, look, save.saveName, null));
    }
}
