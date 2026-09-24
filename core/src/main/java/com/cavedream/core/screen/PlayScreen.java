package com.cavedream.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.CaveDreamGame;
import com.cavedream.core.craft.Recipe;
import com.cavedream.core.fx.Projectile;
import com.cavedream.core.light.GameClock;
import com.cavedream.core.light.LightEngine;
import com.cavedream.core.light.LightSource;
import com.cavedream.core.render.BlockTextures;
import com.cavedream.core.render.PaintedLook;
import com.cavedream.core.render.ItemCatalog;
import com.cavedream.core.render.SkyRenderer;
import com.cavedream.core.save.GameSave;
import com.cavedream.core.inventory.Inventory;
import com.cavedream.core.item.Item;
import com.cavedream.core.player.PlayerClass;
import com.cavedream.core.player.Equipment;
import com.cavedream.core.player.PlayerStats;
import com.cavedream.core.render.UiIcons;
import com.cavedream.core.render.WoodUi;
import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.ItemDrop;
import com.cavedream.core.world.LayerWorld;
import com.cavedream.core.world.PlayerEntity;
import com.cavedream.core.world.Tool;
import com.cavedream.core.world.mob.Mob;
import com.cavedream.core.world.mob.Servant;
import com.cavedream.core.world.mob.Slime;
import com.cavedream.core.world.mob.SpawnManager;

/**
 * L1 浅梦箱庭游玩界面（M2 垂直切片）：可走、可跳、可挖、可放。
 * 操作：A/D 或 ←/→ 移动，W/空格/↑ 跳；左键挖掘、右键放置（射程 6 格）；ESC 回标题。
 */
public class PlayScreen extends ScreenAdapter implements Disposable {

    private static final int TILE = PlayerEntity.TILE;
    private static final float REACH_TILES = 6f;
    private static final float VIEW_HEIGHT_PX = 720f;
    private static final float DEPTH_PER = 0.0085f;   // 每深一格的暗度增量
    private static final float DEPTH_MAX = 0.46f;     // 深度暗度上限（保留底部可读）
    private static final float PICKUP_MAGNET_PX = 3.5f * TILE;   // 拾取吸附半径（初始 3.5 格）
    private static final float MAGNET_SPEED = 260f;             // 吸附飞行速度

    /** 拾取动画残影：从掉落点飞向玩家、逐渐缩小淡出。 */
    private static final class PickupFx {
        float fromX, fromY, t, life;
        Item item;
        PickupFx(float x, float y, Item it) { fromX = x; fromY = y; item = it; life = 0.28f; }
    }
    private static final int PLAYER_GLOW_LEVEL = 12;    // 角色自带微光峰值亮度 0~15
    private static final float PLAYER_GLOW_RADIUS = 6.5f;   // 角色微光半径（格）

    private final CaveDreamGame game;
    private final PlayerClass playerClass;
    private final PlayerStats stats;
    private UiIcons uiIcons;
    private final LayerWorld world;
    private final PlayerEntity player;
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private final OrthographicCamera uiCam = new OrthographicCamera();   // 屏幕坐标（快捷栏 UI）
    private Texture pixel;
    private BitmapFont font;
    private BlockTextures textures;
    private SkyRenderer sky;
    private TextureRegion dreamerRegion;
    private Texture dreamerTex;                                             // 由捏脸上色生成的纹理
    private PaintedLook appearance = new PaintedLook();                     // 可涂色外观（玩家/NPC 通用）
    private TextureRegion blobRegion;
    private TextureRegion pickaxeRegion;
    private TextureRegion coinRegion;
    private BlockType holdBlock = BlockType.DIRT;
    private final Vector3 mouseWorld = new Vector3();
    private final float[] dust = new float[90 * 3];   // x, y, 相位
    private final TextureRegion[] skyRows = new TextureRegion[256];
    private final TextureRegion[] depthRows = new TextureRegion[256];
    private final TextureRegion blobWhiteRegion = new TextureRegion();
    private float time;

    // —— 光照（环境光 + 角色/特效动态光）——
    private final GameClock clock = new GameClock();
    private final LightEngine lightEngine = new LightEngine();
    private final java.util.List<LightSource> dynamicLights = new java.util.ArrayList<>();
    private boolean lightDirty;
    private int daylight0to15 = LightEngine.MAX_LEVEL;   // 当前天光强度（整数级，供 HUD/星尘）
    private double daylight = 1.0;                        // 连续昼光因子 0~1（供光照遮罩平滑渐变）

    // —— 背包 / 挖掘 / 动画 ——
    private static final int HOTBAR = 10;                // 快捷栏格数（背包前 10 格，GDD §3.5）
    private static final int INV_TOTAL = 40;             // 背包总格（含快捷栏）：GDD §3.5 初始 40
    private final Inventory inventory = new Inventory(INV_TOTAL);
    private final Equipment equipment = new Equipment();                     // 装备栏（武器/护甲/饰品）
    private final java.util.List<Recipe> recipes = Recipe.starter();         // 已知配方
    private int invTab;                                                       // E 面板页：0背包 1装备 2工作区
    private float wheelAccum;                                                 // 鼠标滚轮累计（每满 1 格切一次快捷栏）
    private Tool tool = Tool.INITIAL;
    private float frameDelta;
    private int digX = -1, digY = -1;                    // 当前蓄力挖掘目标格
    private float digProgress;                           // 0~1
    private boolean miningNow;                           // 本帧是否在挥镐
    private float walkPhase;                             // 走路动画相位
    private final java.util.List<ItemDrop> drops = new java.util.ArrayList<>();
    private final java.util.List<PickupFx> pickupFx = new java.util.ArrayList<>();
    private final SpawnManager spawner = new SpawnManager(20260923L, 12);   // 昼夜刷怪
    private final int weaponDamage = 12;                                     // 主武器单击伤害（待武器系统细化）
    private float attackCd;                                                   // 攻击冷却
    private boolean showInv;                                                  // E 开合背包
    private boolean showMinimap = true;                                       // Q 开合右上小地图
    private final com.cavedream.core.anim.Swing swing = com.cavedream.core.anim.Swing.chop();   // 挥击基础动作（所有手持物共用）
    // —— 战斗闭环 / 存档 ——
    private final java.util.HashMap<Integer, Integer> edits = new java.util.HashMap<>();   // 改动格 idx→方块id
    private int coins;                                                        // 铸梦币
    private boolean downed;                                                   // 濒死态
    private float downedT;                                                    // 濒死倒计时（秒）
    private float spawnX, spawnY;                                             // 出生点（溃梦复活）
    private final java.util.List<Projectile> projectiles = new java.util.ArrayList<>();   // 远程武器弹道
    private final java.util.List<Servant> servants = new java.util.ArrayList<>();          // 召唤师仆从
    private int servantCap;                                                                 // 仆从上限（基础3+层数+饰品/套装）
    private int servantLayerBonus = 0;                                                      // 每通一层 +1（最多 +5）
    private int servantGearBonus = 0;                                                       // 饰品/套装加成
    private float lastServantClick = -9f;                                                   // 双击收起仆从计时
    private float slashT;                                                     // 近战刀光时长
    private int slashDir;
    private boolean swingHit;                                                 // 本轮挥击是否已在命中帧结算
    private static final int TOOL_DAMAGE = 7;                                 // 工具（镐/斧）近战伤害
    // —— 世界/存档/暂停 ——
    private final long seed;                                                  // 世界种子（存档用）
    private String slot;                                                      // 存档槽位文件名
    private static final int LIGHT_RADIUS = 90;                               // 局部光照重算半径（格）
    private int lightRadius = 100;                                             // 当前重算半径（随视口自适应）
    private int lastLCx = Integer.MIN_VALUE, lastLCy;                         // 上次光照重算中心
    private float saveTimer;                                                  // 自动存档计时
    private boolean paused;                                                   // ESC 暂停菜单
    private String toast;                                                     // 顶部提示（如已存档）
    private float toastT;

    public PlayScreen(CaveDreamGame game, PlayerClass playerClass, LayerWorld world,
                      long seed, int spawnTileX, int spawnTileY, PaintedLook appearance) {
        this.game = game;
        this.playerClass = playerClass;
        this.appearance = appearance != null ? appearance : new PaintedLook();
        this.stats = new PlayerStats(playerClass);
        recomputeServantCap();   // 通灵师上限 3+成长；开局 0 只（需召唤）
        this.world = world;
        this.seed = seed;
        java.util.Random drnd = new java.util.Random(9L);
        for (int i = 0; i < dust.length; i += 3) {
            dust[i] = drnd.nextFloat();
            dust[i + 1] = drnd.nextFloat();
            dust[i + 2] = drnd.nextFloat() * 6.28f;
        }
        player = new PlayerEntity(
                spawnTileX * (float) TILE,
                spawnTileY * (float) TILE + 1f);
        spawnX = player.x();
        spawnY = player.y();

        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        font = CjkFonts.get(15);   // HUD 参数字体（缓存复用，不逐屏重建）
        inventory.add(Item.WOOD_PICKAXE, 1);        // 工具：木镐
        inventory.add(Item.WOOD_AXE, 1);            // 工具：木斧
        inventory.add(playerClass.weapon(), 1);     // 职业主武器（按所选职业）
        inventory.add(Item.ofBlock(BlockType.DIRT), 30);   // 起始方块便于验证放置

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        textures = new BlockTextures();
        uiIcons = new UiIcons();
        sky = new SkyRenderer();
        buildDreamer();
        pickaxeRegion = new TextureRegion(textures.pickaxe());
        coinRegion = new TextureRegion(textures.coin());
        blobRegion = new TextureRegion(textures.cornerBlob());
        blobWhiteRegion.setRegion(new TextureRegion(textures.blobWhite()));
        for (int i = 0; i < 256; i++) {
            skyRows[i] = new TextureRegion(textures.skyGrad(), 0, i, 8, 8);
            depthRows[i] = new TextureRegion(textures.depthGrad(), 0, i, 8, 8);
        }
        lightEngine.recomputeRegion(world, spawnTileX, spawnTileY, LIGHT_RADIUS);   // 初始局部光照（围绕出生点）
        lastLCx = spawnTileX;
        lastLCy = spawnTileY;
    }

    @Override
    public void show() {
        // ScreenAdapter 不是 InputProcessor，用独立 InputAdapter 只接滚轮（其余交互靠轮询）
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                wheelAccum += amountY;   // 每格约 ±1
                return true;
            }
        });
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void resize(int width, int height) {
        float aspect = width / (float) Math.max(1, height);
        camera.viewportHeight = VIEW_HEIGHT_PX;
        camera.viewportWidth = VIEW_HEIGHT_PX * aspect;
        // UI 用固定高度基准(720)、宽按屏幕比例→整套 HUD 作为“设计像素”随窗口等比缩放（不再绝对像素）
        uiCam.setToOrtho(false, VIEW_HEIGHT_PX * aspect, VIEW_HEIGHT_PX);
    }

    /** 鼠标在 UI 虚拟坐标系的坐标（真实像素→uiCam 虚拟单位）。 */
    private float uiMouseX() {
        return Gdx.input.getX() * (uiCam.viewportWidth / Gdx.graphics.getWidth());
    }

    private float uiMouseY() {
        return uiCam.viewportHeight - Gdx.input.getY() * (uiCam.viewportHeight / Gdx.graphics.getHeight());
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            paused = !paused;   // ESC 弹菜单并暂停（怪/伤害一并冻结）
        }
        frameDelta = delta;
        if (toastT > 0f) {
            toastT -= delta;
        }
        if (paused) {
            handlePauseInput();
        } else {
            time += delta;
            updateInput();
            // 特殊规则：天空带（世界顶部 ~18%）重力降低→跳得更高
            player.setGravityScale(player.centerY() / TILE > world.getHeight() * 0.82f ? 0.5f : 1f);
            player.update(world,
                    keyDown(Input.Keys.A) || keyDown(Input.Keys.LEFT),
                    keyDown(Input.Keys.D) || keyDown(Input.Keys.RIGHT),
                    keyDown(Input.Keys.W) || keyDown(Input.Keys.SPACE) || keyDown(Input.Keys.UP),
                    delta);
            if (player.isMoving() && player.isOnGround()) {
                walkPhase += delta * 11f;
            } else {
                walkPhase = 0f;
            }
            updateCamera();
            clock.update(delta);
            daylight0to15 = clock.daylightLevel0to15();
            daylight = clock.daylightFactor();
            ensureLighting();
            rebuildDynamicLights();
            updateDrops(delta);
            stats.update(delta);
            updateMobs(delta);
            updateProjectiles(delta);
            updateServants(delta);
            if (slashT > 0f) {
                slashT -= delta;
            }
            saveTimer += delta;
            if (saveTimer >= 30f) {   // 每 30 秒自动存档
                saveTimer = 0f;
                saveNow();
            }
        }
        renderWorld();
        if (paused) {
            drawPauseMenu();
        }
    }

    /** 绘制世界 + HUD（不含更新）；暂停时画面冻结但仍可见。 */
    private void renderWorld() {
        float d = daylight0to15 / (float) LightEngine.MAX_LEVEL;   // 昼系 0~1
        Gdx.gl.glClearColor(0.043f + 0.32f * d, 0.055f + 0.42f * d, 0.10f + 0.55f * d, 1f);   // 梦夜↔白昼底色
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        sky.draw(batch, uiCam.viewportWidth, uiCam.viewportHeight,
                camera.position.x, time, (float) clock.hour(), d);
        batch.end();
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawVisibleTiles();
        drawMiningProgress();
        drawLighting();
        drawMobs();
        drawDrops();
        drawPlayer();
        renderProjectiles();
        renderServants();
        drawSlashVfx();
        drawPickupFx();
        drawDust();
        drawCursor();
        batch.end();
        drawHud();
        drawHotbar();
        drawStats();
        drawMinimap();
        drawInventory();
        drawToast();
    }

    /** 光照：以相机为中心、覆盖整个视口+余量的方框重算（避免未算区域变黑）；脏或相机移动过大时触发。 */
    private void ensureLighting() {
        int ccx = (int) Math.floor(camera.position.x / TILE);
        int ccy = (int) Math.floor(camera.position.y / TILE);
        int need = (int) (Math.max(camera.viewportWidth, camera.viewportHeight) / TILE / 2f) + 50;
        need = Math.max(need, LIGHT_RADIUS);
        if (lightDirty || need > lightRadius + 8
                || Math.abs(ccx - lastLCx) > 8 || Math.abs(ccy - lastLCy) > 8) {
            lightRadius = need;
            lightEngine.recomputeRegion(world, ccx, ccy, lightRadius);
            lastLCx = ccx;
            lastLCy = ccy;
            lightDirty = false;
        }
    }

    /** 游戏内自动/F5 存档：只写本地主存（快、不打网络）；云端上传留到“保存并退出”（写回缓存）。 */
    private void saveNow() {
        game.saveGame(toSave());
        toast = "已存本地";
        toastT = 2f;
    }

    /** 保存并退出：写本地主存 + 同步上传云端 cache（结果回显到主菜单）。 */
    private void saveAndUpload() {
        GameSave s = toSave();
        game.saveGame(s);
        game.uploadSave(s);   // 同步上传，结果存 lastCloudMsg 供主菜单回显
    }

    private void drawToast() {
        if (toastT <= 0f || toast == null) {
            return;
        }
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        font.setColor(1f, 0.95f, 0.6f, Math.min(1f, toastT));
        font.draw(batch, toast, uiCam.viewportWidth / 2f - 24, uiCam.viewportHeight - 60);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** 暂停菜单三项：返回游戏 / 保存并退出 / 设置。 */
    private static final String[] PAUSE_OPTS = {"返回游戏", "保存并退出", "设置"};

    private float pauseBtnX() {
        return uiCam.viewportWidth / 2f - 130f;
    }

    private float pauseBtnTop() {
        return uiCam.viewportHeight / 2f + (PAUSE_OPTS.length * 70f) / 2f;
    }

    private void handlePauseInput() {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        float mx = uiMouseX(), my = uiMouseY();
        float cx = pauseBtnX(), top = pauseBtnTop(), bw = 260f, bh = 54f, gap = 16f;
        for (int i = 0; i < PAUSE_OPTS.length; i++) {
            float y = top - (i + 1) * (bh + gap) + gap;
            if (mx >= cx && mx <= cx + bw && my >= y && my <= y + bh) {
                if (i == 0) {
                    paused = false;
                } else if (i == 1) {
                    saveAndUpload();
                    game.backToTitle();
                } else {
                    toast = "设置开发中";
                    toastT = 2f;
                }
                return;
            }
        }
    }

    private void drawPauseMenu() {
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        batch.setColor(0, 0, 0, 0.55f);
        batch.draw(pixel, 0, 0, uiCam.viewportWidth, uiCam.viewportHeight);
        batch.setColor(1, 1, 1, 1);
        float bw = 260f, bh = 54f, gap = 16f;
        float cx = pauseBtnX(), top = pauseBtnTop();
        float panelH = PAUSE_OPTS.length * (bh + gap) + 80f;
        WoodUi.panel(batch, pixel, cx - 24f, top - panelH + bh, bw + 48f, panelH);
        font.setColor(0.95f, 0.9f, 0.7f, 1f);
        font.draw(batch, "已暂停", cx, top - 12f);
        font.setColor(1, 1, 1, 1);
        float mx = uiMouseX(), my = uiMouseY();
        for (int i = 0; i < PAUSE_OPTS.length; i++) {
            float y = top - (i + 1) * (bh + gap) + gap;
            boolean hover = mx >= cx && mx <= cx + bw && my >= y && my <= y + bh;
            WoodUi.plank(batch, pixel, cx, y, bw, bh, hover);
            font.setColor(1, 1, 1, 1);
            font.draw(batch, PAUSE_OPTS[i], cx + bw / 2f - 32f, y + bh / 2f + 6f);
        }
        batch.end();
    }

    /** 双条 HUD（屏幕左上）：梦眠=月相图标、魔能=蓝五星；图标透明度=该格填充度（缺=透明、满=实心）。 */
    private void drawStats() {
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        float pip = uiCam.viewportHeight * 0.038f;      // 双条图标相对屏高
        float step = pip * 1.12f;
        float x = pip * 0.6f, top = uiCam.viewportHeight - pip * 0.6f;
        int moons = (int) Math.ceil(stats.maxLucidity() / 10f);
        drawPips(uiIcons.moon(), x, top, step, pip, moons, stats.lucidity() / 10f);
        int stars = (int) Math.ceil(stats.maxMana() / 10f);
        float row2 = top - step * 1.5f;
        drawPips(uiIcons.star(), x, row2, step, pip, stars, stats.mana() / 10f);
        font.setColor(0.9f, 0.92f, 1f, 1f);
        font.draw(batch, stats.lucidity() + "/" + stats.maxLucidity(), x + moons * step + pip * 0.3f, top - pip * 0.3f);
        font.draw(batch, stats.mana() + "/" + stats.maxMana(), x + stars * step + pip * 0.3f, row2 - pip * 0.3f);
        font.setColor(1, 1, 1, 1);
        // 仆从指示（召唤师）：小紫球一排 + 寿命环
        if (!servants.isEmpty()) {
            float iy = row2 - step * 1.4f;
            for (int i = 0; i < servants.size(); i++) {
                batch.setColor(0.7f, 0.55f, 1f, 1f);
                batch.draw(pixel, x + i * step, iy - pip, pip, pip);
                batch.setColor(1, 1, 1, 1);
            }
            font.setColor(0.75f, 0.65f, 0.95f, 1f);
            font.draw(batch, "仆从 " + servants.size(), x + servants.size() * step + pip * 0.3f, iy - pip * 0.3f);
            font.setColor(1, 1, 1, 1);
        }
        // 铸梦币（左上、逐行下移：有仆从时让到仆从行下方，不重叠）
        font.setColor(1f, 0.9f, 0.4f, 1f);
        font.draw(batch, "铸梦币 " + coins, x, top - step * (servants.isEmpty() ? 2.9f : 4.3f));
        // 操作提示（底部居中）
        font.setColor(0.8f, 0.82f, 0.9f, 0.9f);
        font.draw(batch, "E 背包・Q 小地图・F5 存档・左键使用/点快捷栏选格・右键放置",
                uiCam.viewportWidth / 2f - 280f, uiCam.viewportHeight * 0.03f);
        font.setColor(1, 1, 1, 1);
        if (downed) {                                  // 濒死：红暗角 + 倒计时
            batch.setColor(0.45f, 0f, 0f, 0.4f);
            batch.draw(pixel, 0, 0, uiCam.viewportWidth, uiCam.viewportHeight);
            batch.setColor(1, 1, 1, 1);
            font.setColor(1f, 0.45f, 0.45f, 1f);
            font.draw(batch, "濒死… " + (int) Math.ceil(downedT) + "s 后溃梦回出生点",
                    uiCam.viewportWidth / 2f - 130, uiCam.viewportHeight * 0.4f);
            font.setColor(1, 1, 1, 1);
        }
        batch.end();
    }

    /** 一排图标（相对尺寸），第 i 个 alpha = 该格已填充比例（缺=透明、满=实心）。 */
    private void drawPips(Texture tex, float x, float y, float step, float size, int count, float value) {
        for (int i = 0; i < count; i++) {
            float fillAmt = Math.max(0f, Math.min(1f, value - i));   // 第 i 格填充 0~1
            batch.setColor(1, 1, 1, 0.22f + 0.78f * fillAmt);        // 空=淡、满=实
            batch.draw(tex, x + i * step, y - size, size, size);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** 每帧收集动态光源：角色自身微光（保证可玩）+ 未来套装/饰品/时装/武器特效光晕都往这加。 */
    private void rebuildDynamicLights() {
        dynamicLights.clear();
        int px = (int) Math.floor(player.centerX() / TILE);
        int py = (int) Math.floor(player.centerY() / TILE);
        dynamicLights.add(new LightSource(px, py, PLAYER_GLOW_LEVEL, PLAYER_GLOW_RADIUS));
    }

    /** 梦尘：缓慢漂移的半透明光点，只在空气格里可见。 */
    private void drawDust() {
        float halfW = camera.viewportWidth / 2;
        float halfH = camera.viewportHeight / 2;
        float left = camera.position.x - halfW, bottom = camera.position.y - halfH;
        for (int i = 0; i < dust.length; i += 3) {
            float ph = dust[i + 2];
            float wx = left + Math.floorMod((long) (dust[i] * 1280 + time * 7 + Math.sin(time * 0.6 + ph) * 18), 1280L)
                    / 1280f * (halfW * 2);
            float wy = bottom + dust[i + 1] * halfH * 2 + (float) Math.sin(time * 0.8 + ph) * 10;
            int tx = (int) (wx / TILE), ty = (int) (wy / TILE);
            if (!world.inBounds(tx, ty) || world.isSolid(tx, ty)) {
                continue;
            }
            float tw = 0.10f + 0.08f * (float) Math.sin(time * 1.3 + ph);
            tw *= 1f - 0.85f * (daylight0to15 / (float) LightEngine.MAX_LEVEL);   // 白天星星/梦尘淡出
            batch.setColor(0.72f, 0.65f, 0.88f, tw);
            batch.draw(pixel, wx, wy, 2, 2);
        }
        batch.setColor(1, 1, 1, 1);
    }

    private boolean keyDown(int keycode) {
        return Gdx.input.isKeyPressed(keycode);
    }

    /* ---------------- 交互：挖掘 / 放置 ---------------- */

    private void updateInput() {
        // 数字键 1~9、 然 0 切换快捷格
        for (int i = 0; i < HOTBAR; i++) {
            if (Gdx.input.isKeyJustPressed(numKey(i))) {
                inventory.select(i);
            }
        }
        // 滚轮快速切换快捷栏（循环，仅前 HOTBAR 格）：换工具/武器超方便
        int hcnt = Math.min(HOTBAR, inventory.size());
        while (wheelAccum >= 1f) {
            inventory.select((inventory.selected() + 1) % hcnt);
            wheelAccum -= 1f;
        }
        while (wheelAccum <= -1f) {
            inventory.select((inventory.selected() - 1 + hcnt) % hcnt);
            wheelAccum += 1f;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            showInv = !showInv;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            showMinimap = !showMinimap;   // Q 开关小地图
        }
        if (showInv) {                       // 背包开启：只处理面板内点击，不挖掘/放置/攻击
            miningNow = false;
            digProgress = 0f;
            handleInventoryClick();
            return;
        }
        if (downed) {                          // 濒死：不可挖掘/攻击/放置（仅可移动，移速已降）
            miningNow = false;
            return;
        }
        // 左键落在快捷栏→只选格（不触发使用/挖掘），方便鼠标党/Boss 战快速切槽
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            if (minimapToggleHit()) {                    // 点小地图“−/+”→开合
                showMinimap = !showMinimap;
                return;
            }
            if (servantIndicatorHit()) {                 // 双击左上仆从栏→收起全部（重召唤用召唤书）
                if (time - lastServantClick < 0.4f) {
                    servants.clear();
                    lastServantClick = -9f;
                    toast = "仆从已收起";
                    toastT = 1f;
                } else {
                    lastServantClick = time;
                }
                return;
            }
            int hb = hotbarSlotAtMouse();
            if (hb >= 0) {
                inventory.select(hb);
                return;
            }
        }
        int[] tile = mouseTile();
        Item held = inventory.selectedItem();
        Tool heldTool = held == null ? null : Tool.byItemId(held.id());
        if (held != null && held.placeable()) {
            holdBlock = held.block();
        }
        if (heldTool != null) {
            tool = heldTool;
        }

        boolean leftHeld = Gdx.input.isButtonPressed(Input.Buttons.LEFT);
        boolean leftPressed = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);

        // 使用手持物：冷却就绪且（点按或按住且上一挥已结束）→起新一轮挥击；伤害在“命中帧”结算（与动画同步）
        if ((leftPressed || (leftHeld && !swing.isActive())) && attackCd <= 0f) {
            faceTowardMouse();                                              // 起手即朝鼠标转身
            swing.restart();
            swingHit = false;
            attackCd = attackCooldown(held);
        }
        swing.update(frameDelta);
        if (swing.isActive()) {
            faceTowardMouse();                                              // 武器在动→持续跟随鼠标朝向
        }
        if (!swingHit && swing.isActive() && swing.progress() >= 0.5f) {
            applyAttack(held);                                                 // 挥到一半（刃到位）才判定
            swingHit = true;
        }

        // 挖掘：仅当手持工具（镐/斧）且按住左键指向可挖方块时蓄力
        boolean mining = false;
        if (leftHeld && tile != null && heldTool != null) {
            BlockType b = world.blockAt(tile[0], tile[1]);
            float need = heldTool.digSeconds(b);
            if (need >= 0f) {
                if (tile[0] != digX || tile[1] != digY) {
                    digX = tile[0];
                    digY = tile[1];
                    digProgress = 0f;
                }
                digProgress += frameDelta / Math.max(0.03f, need);
                mining = true;
                if (digProgress >= 1f) {
                    world.setBlock(tile[0], tile[1], BlockType.AIR);
                    edits.put(tile[1] * world.getWidth() + tile[0], (int) BlockType.AIR.id());   // 记录改动供存档
                    spawnDrop(tile[0], tile[1], Item.ofBlock(b));   // 掉成地上的物品，走近再拾取
                    lightDirty = true;
                    digProgress = 0f;
                    digX = -1;
                    digY = -1;
                }
            } else {
                digProgress = 0f;
                digX = -1;
                digY = -1;
            }
        } else {
            digProgress = 0f;
            digX = -1;
            digY = -1;
        }
        miningNow = mining;

        // 右键：放置方块 / 交互
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && tile != null
                && held != null && held.placeable()
                && world.blockAt(tile[0], tile[1]) == BlockType.AIR
                && !player.overlapsTile(tile[0], tile[1])) {
            world.setBlock(tile[0], tile[1], held.block());
            edits.put(tile[1] * world.getWidth() + tile[0], (int) held.block().id());   // 记录改动供存档
            inventory.takeSelectedOne();
            lightDirty = true;
        }
    }

    /** 快捷格序号 0~9 对应数字键：0→NUM_1 … 8→NUM_9、 9→NUM_0。 */
    private static int numKey(int i) {
        return i < 9 ? Input.Keys.NUM_1 + i : Input.Keys.NUM_0;
    }

    /** 鼠标指向的 tile 格；超出挖掘射程或越界返回 null。 */
    private int[] mouseTile() {
        mouseWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorld);
        int tx = (int) Math.floor(mouseWorld.x / TILE);
        int ty = (int) Math.floor(mouseWorld.y / TILE);
        if (!world.inBounds(tx, ty)) {
            return null;
        }
        float dx = tx + 0.5f - player.centerX() / TILE;
        float dy = ty + 0.5f - player.centerY() / TILE;
        return dx * dx + dy * dy <= REACH_TILES * REACH_TILES ? new int[]{tx, ty} : null;
    }

    /* ---------------- 渲染 ---------------- */

    private void updateCamera() {
        float halfW = camera.viewportWidth / 2;
        float halfH = camera.viewportHeight / 2;
        float worldW = world.getWidth() * (float) TILE;
        float worldH = world.getHeight() * (float) TILE;
        camera.position.x = clamp(player.centerX(), halfW, Math.max(halfW, worldW - halfW));
        // 机位：人物落在屏高 68%（自上）= 32%（自底），头顶留更多视野看地形/Boss（v 由 61.8% 再下移）
        float lookY = player.centerY() + 0.36f * halfH;
        camera.position.y = clamp(lookY, halfH, Math.max(halfH, worldH - halfH));
        camera.update();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void drawVisibleTiles() {
        float halfW = camera.viewportWidth / 2;
        float halfH = camera.viewportHeight / 2;
        int x0 = (int) Math.floor((camera.position.x - halfW) / TILE);
        int x1 = (int) Math.ceil((camera.position.x + halfW) / TILE);
        int y0 = (int) Math.floor((camera.position.y - halfH) / TILE);
        int y1 = (int) Math.ceil((camera.position.y + halfH) / TILE);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                BlockType b = world.blockAt(x, y);
                if (b == BlockType.AIR) {
                    continue;
                }
                float px = x * (float) TILE, py = y * (float) TILE;
                batch.setColor(1, 1, 1, 1);
                batch.draw(tileRegion(b, x, y), px, py, TILE, TILE);
                if (b == BlockType.FOG) {
                    continue;   // 雾界自成一体，不叠加地形容错
                }
                if (b == BlockType.WATER) {
                    if (!world.blockAt(x, y + 1).solid()) {   // 水面亮线
                        float[] w = BlockTextures.lightOf(b, 1.45);
                        fill(px, py + TILE - 1, TILE, 1, w[0], w[1], w[2], 0.9f);
                    }
                    batch.setColor(1, 1, 1, 1);
                    continue;
                }
                drawTerrain(x, y, b, px, py);
            }
        }
    }

    /** 固体地形的卡通 autotiling：同类无缝、异材过渡、受光/描边/圆角/AO（明暗由 drawLighting 全局处理）。 */
    private void drawTerrain(int x, int y, BlockType b, float px, float py) {
        BlockType nU = world.blockAt(x, y + 1), nD = world.blockAt(x, y - 1);
        BlockType nL = world.blockAt(x - 1, y), nR = world.blockAt(x + 1, y);
        boolean oU = !nU.solid(), oD = !nD.solid(), oL = !nL.solid(), oR = !nR.solid();
        // 1) 不同材质→接触渗色（body 相同视同一体，不渗，避免草/土接缝）
        if (nU.solid() && body(nU) != body(b)) {
            transition(nU, x, y, px, py, 0);
        }
        if (nD.solid() && body(nD) != body(b)) {
            transition(nD, x, y, px, py, 1);
        }
        if (nL.solid() && body(nL) != body(b)) {
            transition(nL, x, y, px, py, 2);
        }
        if (nR.solid() && body(nR) != body(b)) {
            transition(nR, x, y, px, py, 3);
        }
        // 2) 顶面受光 / 草唇（仅朝开阔空间的上边）
        float[] ol = BlockTextures.outlineOf(b);
        if (oU) {
            if (b == BlockType.GRASS) {
                batch.setColor(1, 1, 1, 1);
                batch.draw(textures.grassLip(), px, py + TILE - 8, TILE, 8);   // 草唇顶边对齐格顶、向下垂入→贴合地表不外探
            } else {
                float[] hl = BlockTextures.lightOf(b, 1.30);
                fill(px, py + TILE - 2, TILE, 2, hl[0], hl[1], hl[2], 0.42f);    // 顶面高光
            }
        }
        // 4) 内侧 AO：朝空的左右边靠内壁处加一道窄阴影（增强厚度/层次）
        if (oU) {
            if (oL) {
                fill(px + 1, py, 1, TILE, 0, 0, 0, 0.16f);
            }
            if (oR) {
                fill(px + TILE - 2, py, 1, TILE, 0, 0, 0, 0.16f);
            }
        }
        // 5) 轮廓描边（只画朝开阔空间的边 → 干净剪影，同类内部无线）
        batch.setColor(ol[0], ol[1], ol[2], 1f);
        if (oU && b != BlockType.GRASS) {
            batch.draw(pixel, px, py + TILE - 1, TILE, 1);
        }
        if (oD) {
            batch.draw(pixel, px, py, TILE, 1);
        }
        if (oL) {
            batch.draw(pixel, px, py, 1, TILE);
        }
        if (oR) {
            batch.draw(pixel, px + TILE - 1, py, 1, TILE);
        }
        batch.setColor(1, 1, 1, 1);
        // 凸角圆角暂不处理：旧实现在格外侧贴圆角盘→变成"耳朵"，待用"向内修圆"重做。
    }

    /** 不同材质边界：邻居色按列噪声“渗”入本格 1~3px，形成有机过渡而非直线切口。 */
    private void transition(BlockType nb, int x, int y, float px, float py, int side) {
        float r = nb.r() * 0.62f, g = nb.g() * 0.62f, bl = nb.b() * 0.62f;   // 偏暗=接触阴影
        for (int k = 0; k < TILE; k += 2) {
            int depth = 1 + (int) (hash(x * 4 + side, y * 4 + k + side * 7) * 3f);   // 1~3 px
            switch (side) {
                case 0 -> fill(px + k, py + TILE - depth, 2, depth, r, g, bl, 0.9f);
                case 1 -> fill(px + k, py, 2, depth, r, g, bl, 0.9f);
                case 2 -> fill(px, py + k, depth, 2, r, g, bl, 0.9f);
                case 3 -> fill(px + TILE - depth, py + k, depth, 2, r, g, bl, 0.9f);
                default -> { }
            }
        }
    }

    /** 凸角补圆：两相邻朝空边的交角用本体色圆角盘覆盖直角。 */
    private void cornerRound(BlockType b, float px, float py, boolean oU, boolean oD, boolean oL, boolean oR) {
        float[] bodyc = BlockTextures.lightOf(b, 0.98);
        batch.setColor(bodyc[0], bodyc[1], bodyc[2], 1f);
        if (oU && oL) {
            batch.draw(blobWhiteRegion, px - 7, py + TILE - 4, 8, 8);
        }
        if (oU && oR) {
            batch.draw(blobWhiteRegion, px + TILE - 1, py + TILE - 4, 4, 4, 8, 8, -1, 1, 0);
        }
        if (oD && oL) {
            batch.draw(blobWhiteRegion, px - 7, py - 4, 4, 4, 8, 8, 1, -1, 0);
        }
        if (oD && oR) {
            batch.draw(blobWhiteRegion, px + TILE - 1, py - 4, 4, 4, 8, 8, -1, -1, 0);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** GRASS 视为 DIRT 的地表变体（共用本体色），二者之间不算“异材”、不出接缝。 */
    private static BlockType body(BlockType b) {
        return b == BlockType.GRASS ? BlockType.DIRT : b;
    }

    private void fill(float x, float y, float w, float h, float r, float g, float b, float a) {
        batch.setColor(r, g, b, a);
        batch.draw(pixel, x, y, w, h);
    }

    private static float hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFF) / 65535f;
    }

    private static float clampf(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampi(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }


    /** 全局光照遮罩：每格亮度=静态(天光+块光)与动态(角色/特效光)合成，按 16 级换算为暗度叠加。 */
    private void drawLighting() {
        float halfW = camera.viewportWidth / 2;
        float halfH = camera.viewportHeight / 2;
        int x0 = (int) Math.floor((camera.position.x - halfW) / TILE);
        int x1 = (int) Math.ceil((camera.position.x + halfW) / TILE);
        int y0 = (int) Math.floor((camera.position.y - halfH) / TILE);
        int y1 = (int) Math.ceil((camera.position.y + halfH) / TILE);
        x0 = Math.max(0, x0); x1 = Math.min(world.getWidth() - 1, x1);
        y0 = Math.max(0, y0); y1 = Math.min(world.getHeight() - 1, y1);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                // 开阔天空（空气且天光满格）不叠世界暗纱→太阳/天空由 SkyRenderer 干净呈现，不被“阴影”盖住。
                if (world.blockAt(x, y) == BlockType.AIR && lightEngine.skyAt(x, y) >= LightEngine.MAX_LEVEL) {
                    continue;
                }
                float dark = darknessAt(x, y);
                if (dark > 0.004f) {
                    fill(x * (float) TILE, y * (float) TILE, TILE, TILE, 0.06f, 0.07f, 0.13f, dark);
                }
            }
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** 该格暗度 0~1（=1-亮度/15）；亮度受环境天光/块光与角色、特效等动态光共同影响。 */
    private float darknessAt(int x, int y) {
        float a = 1f - lightLevelFloat(x, y);   // 连续亮度→连续暗度，不再按 16 级量化
        return a > 0.9f ? 0.9f : a;             // 最暗也留 10% 可见
    }

    /** 连续亮度 0~1（double/float）：天光×昼光因子、块光、动态光取最大（不再用太阳直射角投影）。 */
    private float lightLevelFloat(int x, int y) {
        float sky = lightEngine.skyAt(x, y) / (float) LightEngine.MAX_LEVEL * (float) daylight;
        float lvl = Math.max(sky, lightEngine.blockAt(x, y) / (float) LightEngine.MAX_LEVEL);
        for (int i = 0, n = dynamicLights.size(); i < n; i++) {
            float c = dynamicLights.get(i).at(x, y) / (float) LightEngine.MAX_LEVEL;
            if (c > lvl) {
                lvl = c;
            }
        }
        return lvl > 1f ? 1f : lvl;
    }

    /** 整数亮度 0~15（对外 1~16 级 = +1），供 HUD “我处亮度”。 */
    private int lightLevelAt(int x, int y) {
        return Math.round(lightLevelFloat(x, y) * LightEngine.MAX_LEVEL);
    }

    /** 材质基础贴图：有 DB 图集时优先用（像素来自表），否则回退程序生成。 */
    private TextureRegion tileRegion(BlockType b, int x, int y) {
        ItemCatalog cat = ItemCatalog.get();
        if (cat != null && cat.ready()) {
            TextureRegion r = cat.region(b, x, y);
            if (r != null) {
                return r;
            }
        }
        return textures.region(b, x, y);
    }

    private void drawPlayer() {
        float w = player.width();
        float h = player.height();
        int facing = player.facing();
        boolean walking = player.isMoving() && player.isOnGround();
        // 走路：上下颠 + 左右轻摆（非纸片平移）；空中：前倾
        float bob = walking ? (float) Math.abs(Math.sin(walkPhase)) * 2.2f : 0f;
        float tilt;
        if (walking) {
            tilt = (float) Math.sin(walkPhase) * 3f;
        } else if (!player.isOnGround()) {
            tilt = facing > 0 ? 6f : -6f;
        } else {
            tilt = 0f;
        }
        // 手持物：按类型定尺寸与握姿（武器大、工具中、方块为小立方抱于胸前），配合走路持手轻晃与挥砍弧
        Item held = inventory.selectedItem();
        Item.Kind hk = held == null ? Item.Kind.TOOL : held.kind();
        float ps, rest, gy, gripF;
        if (hk == Item.Kind.WEAPON) {
            ps = h * 0.92f; rest = 22f; gy = player.centerY() + h * 0.18f; gripF = 0.5f;
        } else if (hk == Item.Kind.BLOCK) {
            ps = h * 0.44f; rest = -12f; gy = player.centerY() + h * 0.02f; gripF = 0.42f;   // 小立方靠胸前
        } else {
            ps = h * 0.62f; rest = 26f; gy = player.centerY() + h * 0.12f; gripF = 0.5f;
        }
        TextureRegion hand = held != null ? itemSprite(held) : pickaxeRegion;
        float sway = walking ? (float) Math.sin(walkPhase * 2f) : 0f;        // 持手随步轻晃
        gy += bob + sway * 0.8f;
        float rot = hk == Item.Kind.BLOCK
                ? rest + sway * 3f                                            // 方块只随步微晃、不挥砍
                : swing.angle(rest, facing);                                 // 武器/工具：静止握持角 + 头顶→身前下挥砍弧
        float gx = player.centerX() + facing * w * gripF;
        batch.draw(hand, gx - ps / 2f, gy, ps / 2f, 0f, ps, ps, facing, 1f, rot);
        // 侧脸身体贴图默认朝右；scaleX=facing 镜像，tilt 为小幅旋转（身体盖在握柄上→更像手持）
        if (stats.isInvulnerable()) {                        // 受击无敌帧→闪白
            batch.setColor(1f, 0.55f, 0.55f, 1f);
        } else {
            batch.setColor(1, 1, 1, 1);
        }
        batch.draw(dreamerRegion, player.x(), player.y() + bob, w / 2, h / 2, w, h, facing, 1, tilt);
        batch.setColor(1, 1, 1, 1);
    }

    /** 挖掘进度：目标格逐“凿深”的暗置 + 顶部进度条。 */
    private void drawMiningProgress() {
        if (digX < 0 || digProgress <= 0f) {
            return;
        }
        float px = digX * (float) TILE, py = digY * (float) TILE;
        fill(px, py, TILE, TILE, 0f, 0f, 0f, digProgress * 0.55f);
        batch.setColor(1f, 0.9f, 0.3f, 0.95f);
        batch.draw(pixel, px, py + TILE - 2, TILE * Math.min(1f, digProgress), 2f);
        batch.setColor(1, 1, 1, 1);
    }

    /** 挖碎方块→在地上生成一个掉落物。 */
    private void spawnDrop(int tileX, int tileY, Item it) {
        if (it == null) {
            return;
        }
        drops.add(new ItemDrop(tileX * (float) TILE + (TILE - ItemDrop.SIZE) / 2f,
                tileY * (float) TILE + 2f, it, 1));
    }

    /** 击杀掉银色铸梦币（数量由怪物自身 rollCoins 决定，随种类而变）。 */
    private void spawnCoinDrop(Mob m) {
        drops.add(new ItemDrop(m.centerX() - ItemDrop.SIZE / 2f, m.centerY() - ItemDrop.SIZE / 2f,
                Item.COIN, m.rollCoins()));
    }

    /** 掉落物物理 + 吸附 + 拾取（入包才消失，背包满则留地）。 */
    private void updateDrops(float dt) {
        float pcx = player.centerX(), pcy = player.centerY();
        for (int i = drops.size() - 1; i >= 0; i--) {
            ItemDrop d = drops.get(i);
            float dx = d.centerX() - pcx, dy = d.centerY() - pcy;
            if (dx * dx + dy * dy < PICKUP_MAGNET_PX * PICKUP_MAGNET_PX) {
                d.seek(pcx, pcy, MAGNET_SPEED);
            }
            d.update(world, dt);
            if (d.overlapsRect(player.x(), player.y(), player.width(), player.height())) {
                if (d.item().kind() == Item.Kind.COIN) {   // 铸梦币→直接入账，不占背包
                    coins += d.count();
                    pickupFx.add(new PickupFx(d.centerX(), d.centerY(), d.item()));
                    drops.remove(i);
                    continue;
                }
                if (inventory.add(d.item(), d.count())) {
                    pickupFx.add(new PickupFx(d.centerX(), d.centerY(), d.item()));
                    drops.remove(i);
                    continue;
                }
            }
            if (d.age() > 300f) {
                drops.remove(i);
            }
        }
    }

    /** 地上的掉落物：方块用块图、工具/武器用镐子图；浮动 + 微旋转。 */
    private void drawDrops() {
        for (int i = 0; i < drops.size(); i++) {
            ItemDrop d = drops.get(i);
            float size = 12f;
            float bob = (float) Math.sin(d.age() * 5f + i) * 1.5f;
            float cx = d.centerX(), cy = d.centerY() + bob;
            batch.setColor(1, 1, 1, 1);
            batch.draw(itemSprite(d.item()), cx - size / 2, cy - size / 2, size / 2, size / 2, size, size,
                    1, 1, (float) Math.sin(d.age() * 2f) * 6f);
            if (d.count() > 1) {
                font.draw(batch, String.valueOf(d.count()), cx + size / 2 - 4, cy + size / 2);
            }
        }
    }

    /** 命中帧触发的攻击效果（按职业武器；工具也能近战；方块仅挥）。 */
    private void applyAttack(Item held) {
        float pcx = player.centerX(), pcy = player.centerY();
        if (held == null) {
            return;
        }
        if (held.kind() == Item.Kind.WEAPON) {
            float[] a = aimVec();
            switch (held.id()) {
                case 200:                                  // 战士：宽弧近战扫击
                    meleeSwing(weaponDamage + 6, 1.15f);
                    break;
                case 203:                                  // 射手：箭
                    spawnProjectile(pcx, pcy, a, 780, weaponDamage, 1.1f, Projectile.ARROW);
                    break;
                case 201:                                  // 法师：魔法弹（耗魔）
                    if (stats.spendMana(2)) {
                        spawnProjectile(pcx, pcy, a, 540, weaponDamage + 8, 1.3f, Projectile.BOLT);
                    } else {
                        toast = "魔能不足";
                        toastT = 1f;
                    }
                    break;
                case 202:                                  // 通灵者：用召唤书召唤一只仆从（上限 servantCap，耗魔）
                    if (servants.size() >= servantCap) {
                        toast = "仆从已达上限";
                        toastT = 1f;
                    } else if (stats.spendMana(1)) {
                        summonServant();
                    } else {
                        toast = "魔能不足";
                        toastT = 1f;
                    }
                    break;
                case 204:                                  // 刺客：飞刀
                    spawnProjectile(pcx, pcy, a, 760, weaponDamage, 0.5f, Projectile.DAGGER);
                    break;
                default:
                    meleeSwing(weaponDamage, 1f);
                    break;
            }
        } else if (held.kind() == Item.Kind.TOOL) {        // 工具（镐/斧）也能拍怪
            meleeSwing(TOOL_DAMAGE, 1f);
        }
    }

    /** 一次攻击的冷却（= 两轮挥击最小间隔）。 */
    private float attackCooldown(Item held) {
        if (held == null) {
            return 0.35f;
        }
        if (held.kind() == Item.Kind.WEAPON) {
            switch (held.id()) {
                case 200: return 0.42f;
                case 203: return 0.34f;
                case 201: return 0.4f;
                case 202: return 0.5f;
                case 204: return 0.28f;
                default: return 0.4f;
            }
        }
        if (held.kind() == Item.Kind.TOOL) {
            return 0.4f;
        }
        return 0.3f;
    }

    /** 按鼠标世界坐标与角色的左右相对位置自动转身（鼠标在左→朝左，在右→朝右）。 */
    private void faceTowardMouse() {
        mouseWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorld);
        player.setFacing(mouseWorld.x >= player.centerX() ? 1 : -1);
    }

    /** 胛准单位向量（玩家中心→鼠标世界坐标）。 */
    private float[] aimVec() {
        mouseWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorld);
        float dx = mouseWorld.x - player.centerX();
        float dy = mouseWorld.y - player.centerY();
        float len = (float) Math.hypot(dx, dy);
        if (len < 1e-3f) {
            return new float[]{player.facing(), 0f};
        }
        return new float[]{dx / len, dy / len};
    }

    private void spawnProjectile(float x, float y, float[] dir, float speed, int dmg, float life, int kind) {
        projectiles.add(new Projectile(x, y, dir[0] * speed, dir[1] * speed, dmg, life, kind));
    }

    /** 召唤一只仆从（按现有数错开环绕相位）。 */
    private void summonServant() {
        float phase = (float) (servants.size() * (2 * Math.PI / Math.max(1, servantCap)));
        servants.add(new Servant(player.centerX(), player.centerY() + 20f, 30, weaponDamage + 2, phase));
    }

    /** 仆从上限 = 基础3 + 层数加成(≤5) + 饰品/套装加成（仅通灵师）。 */
    private void recomputeServantCap() {
        servantCap = playerClass == PlayerClass.SUMMONER
                ? 3 + Math.min(5, servantLayerBonus) + servantGearBonus : 0;
    }

    /** 近战弧击：面向半平面内、射程上、且视线不被方块遮挡的怪才受伤 + 击退 + 出刀光。 */
    private void meleeSwing(int dmg, float reachMul) {
        float pcx = player.centerX(), pcy = player.centerY();
        float reach = REACH_TILES * TILE * reachMul;
        int f = player.facing();
        for (Mob m : spawner.mobs()) {
            float dx = m.centerX() - pcx, dy = m.centerY() - pcy;
            if (Math.hypot(dx, dy) <= reach && dx * f > -4
                    && losClear(pcx, pcy, m.centerX(), m.centerY())) {
                m.hurt(dmg, dx >= 0 ? 1f : -1f);        // 掉币统一由 updateMobs 的 drainKilled 结算
            }
        }
        slashT = 0.18f;
        slashDir = f;
    }

    /** 两点间视线是否无固体遮挡（近战不能隔墙打人）。 */
    private boolean losClear(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0, dy = y1 - y0;
        float dist = (float) Math.hypot(dx, dy);
        int steps = (int) (dist / (TILE * 0.5f));
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            int tx = (int) ((x0 + dx * t) / TILE), ty = (int) ((y0 + dy * t) / TILE);
            if (world.isSolid(tx, ty)) {
                return false;
            }
        }
        return true;
    }

    /** 仆从推进（跟随/索敌/攻击，到期或死亡移除）。 */
    private void updateServants(float dt) {
        float pcx = player.centerX(), pcy = player.centerY();
        for (java.util.Iterator<Servant> it = servants.iterator(); it.hasNext(); ) {
            if (!it.next().update(pcx, pcy, spawner.mobs(), dt)) {
                it.remove();
            }
        }
    }

    /** 仆从渲染：发光紫球 + 核心 + 生命条。 */
    private void renderServants() {
        for (Servant sv : servants) {
            float x = sv.x(), y = sv.y();
            batch.setColor(0.7f, 0.55f, 1f, 0.35f);
            batch.draw(pixel, x - 9, y - 9, 18, 18);          // 光晕
            batch.setColor(0.88f, 0.78f, 1f, 1f);
            batch.draw(pixel, x - 5, y - 5, 10, 10);          // 核心
            batch.setColor(1, 1, 1, 1);
            if (sv.hp() < sv.maxHp()) {
                batch.setColor(0.1f, 0.1f, 0.1f, 0.8f);
                batch.draw(pixel, x - 8, y + 11, 16, 2);
                batch.setColor(0.6f, 0.9f, 1f, 1f);
                batch.draw(pixel, x - 8, y + 11, 16 * (sv.hp() / (float) sv.maxHp()), 2);
            }
        }
    }

    /** 弹道推进 + 命中怪结算。 */
    private void updateProjectiles(float dt) {
        for (java.util.Iterator<Projectile> it = projectiles.iterator(); it.hasNext(); ) {
            Projectile p = it.next();
            if (!p.update(world, dt)) {
                it.remove();
                continue;
            }
            for (Mob m : spawner.mobs()) {
                if (m.isAlive() && p.hitsRect(m.x(), m.y(), m.w(), m.h())) {
                    m.hurt(p.dmg, p.vx >= 0 ? 1f : -1f);   // 掉币统一由 drainKilled 结算
                    it.remove();
                    break;
                }
            }
        }
    }

    /** 渲染弹道（世界坐标，随速度方向拉长的发光小条）。 */
    private void renderProjectiles() {
        for (Projectile p : projectiles) {
            float r, g, b;
            if (p.kind == Projectile.BOLT) {                       // 法师：青蓝
                r = 0.45f; g = 0.7f; b = 1f;
            } else if (p.kind == Projectile.ORB) {                 // 通灵：紫
                r = 0.7f; g = 0.5f; b = 1f;
            } else if (p.kind == Projectile.DAGGER) {              // 刺客：冷银
                r = 0.8f; g = 0.85f; b = 0.95f;
            } else {                                               // 箭：木褐
                r = 0.7f; g = 0.5f; b = 0.28f;
            }
            float ang = (float) Math.toDegrees(Math.atan2(p.vy, p.vx));
            float len = p.kind == Projectile.ARROW ? 14f : 10f;
            float thick = p.kind == Projectile.ARROW ? 2.5f : 6f;
            batch.setColor(r, g, b, 1f);
            batch.draw(pixel, p.x - len / 2f, p.y - thick / 2f, len, thick);
            batch.setColor(r, g, b, 0.35f);                        // 光晕
            batch.draw(pixel, p.x - 5, p.y - 5, 10, 10);
            batch.setColor(1, 1, 1, 1);
        }
    }

    /** 近战刀光：面前一段渐隐弧。 */
    private void drawSlashVfx() {
        if (slashT <= 0f) {
            return;
        }
        float k = slashT / 0.18f;
        float cx = player.centerX() + slashDir * 22f;
        float cy = player.centerY();
        batch.setColor(1f, 1f, 0.85f, 0.7f * k);
        for (int i = -2; i <= 2; i++) {
            float ang = (float) Math.toRadians(i * 26f * slashDir);
            float rr = 26f;
            batch.draw(pixel, cx + (float) Math.cos(ang) * rr - 3, cy + (float) Math.sin(ang) * rr - 3, 6, 6);
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** 刷怪推进 + 接触伤害 + 溃梦复活。 */
    private void updateMobs(float dt) {
        attackCd = Math.max(0f, attackCd - dt);
        spawner.update(world, player.centerX(), player.centerY(), clock.isNight(), dt);
        for (Mob m : spawner.drainKilled()) {          // 任何来源击杀→集中掉 2~4 枚铸梦币
            spawnCoinDrop(m);
        }
        for (Mob m : spawner.mobs()) {                       // 接触伤害：stats.hurt 自带无敌帧（防灌伤）
            if (m.overlapsRect(player.x(), player.y(), player.width(), player.height())) {
                stats.hurt(m instanceof Slime ? ((Slime) m).touchDamage() : 5);
                break;
            }
        }
        if (stats.isDreamBreak() && !downed) {           // 梦眠归零 → 进入 10 秒濒死
            downed = true;
            downedT = 10f;
            player.setSpeedScale(0.4f);
        }
        if (downed) {
            downedT -= dt;
            if (downedT <= 0f) {                          // 未救回 → 溃梦：回出生点复活
                downed = false;
                player.setSpeedScale(1f);
                stats.reviveAtAnchor();
                player.setPos(spawnX, spawnY);
                spawner.mobs().removeIf(m -> Math.hypot(m.centerX() - spawnX, m.centerY() - spawnY) < 12 * TILE);
            }
        }
    }

    /** 目标格上的怪（供左键攻击）；无则 null。 */
    private Mob mobAt(int tileX, int tileY) {
        float tx = tileX * (float) TILE, ty = tileY * (float) TILE;
        for (Mob m : spawner.mobs()) {
            if (m.overlapsRect(tx, ty, TILE, TILE)) {
                return m;
            }
        }
        return null;
    }

    /** 史莱姆：彩色身体（落地压扁/腾空拉长）+ 高光 + 眼睛 + 受伤血条。 */
    private void drawMobs() {
        for (Mob m : spawner.mobs()) {
            if (!(m instanceof Slime)) {
                continue;
            }
            Slime s = (Slime) m;
            int rgb = s.rgb();
            float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
            if (m.isInvulnerable()) {                       // 无敌帧→闪白
                r = (r + 1f) / 2f; g = (g + 1f) / 2f; b = (b + 1f) / 2f;
            }
            float squash = s.isOnGround() ? 1.15f : 0.85f;
            float w = s.w() * squash, h = s.h() * (2f - squash);
            float x = s.x() + (s.w() - w) / 2f, y = s.y();
            batch.setColor(r, g, b, 1f);
            batch.draw(pixel, x, y, w, h);
            batch.setColor(Math.min(1f, r + 0.25f), Math.min(1f, g + 0.25f), Math.min(1f, b + 0.25f), 1f);
            batch.draw(pixel, x, y + h - 3, w, 3);                       // 顶部高光
            batch.setColor(1, 1, 1, 1);
            batch.draw(pixel, x + w * 0.25f, y + h * 0.5f, 3, 4);        // 眼
            batch.draw(pixel, x + w * 0.6f, y + h * 0.5f, 3, 4);
            if (s.hp() < s.maxHp()) {                                    // 血条
                batch.setColor(0.1f, 0.1f, 0.1f, 0.8f);
                batch.draw(pixel, x, y + h + 3, w, 3);
                batch.setColor(0.85f, 0.25f, 0.25f, 1f);
                batch.draw(pixel, x, y + h + 3, w * (s.hp() / (float) s.maxHp()), 3);
            }
            batch.setColor(1, 1, 1, 1);
        }
    }

    /** 拾取动画：物品图标从掉落点缩飞至玩家中心。 */
    private void drawPickupFx() {
        float pcx = player.centerX(), pcy = player.centerY();
        for (int i = pickupFx.size() - 1; i >= 0; i--) {
            PickupFx f = pickupFx.get(i);
            f.t += frameDelta;
            float k = f.t / f.life;
            if (k >= 1f) {
                pickupFx.remove(i);
                continue;
            }
            float x = f.fromX + (pcx - f.fromX) * k;
            float y = f.fromY + (pcy - f.fromY) * k;
            float size = 12f * (1f - k * 0.6f);
            batch.setColor(1, 1, 1, 1f - k);
            batch.draw(itemSprite(f.item), x - size / 2, y - size / 2, size, size);
            batch.setColor(1, 1, 1, 1);
        }
    }

    /** 物品图标：可放置方块用其贴图，否则按 id 取镐/斧/职业武器图。 */
    private TextureRegion itemSprite(Item it) {
        if (it.kind() == Item.Kind.COIN) {
            return coinRegion;
        }
        if (it.placeable()) {
            return tileRegion(it.block(), 0, 0);
        }
        Texture t = uiIcons.iconFor(it);
        return t != null ? new TextureRegion(t) : pickaxeRegion;
    }

    /** 背包一行的列数与单格尺寸（相对窗口）。 */
    private static final int INV_COLS = 10;

    /** 背包面板（E 开合）：左侧竖排标签（背包/合成/装备与饰品）+ 右侧内容。木质公告牌。 */
    private void drawInventory() {
        if (!showInv) {
            return;
        }
        float[] L = invLayout();
        float slot = L[0], gap = L[1], sideW = L[2], panelW = L[5], panelH = L[6], px = L[7], py = L[8], pad = L[11];
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        WoodUi.panel(batch, pixel, px, py, panelW, panelH);
        String[] tabs = {"背包", "合成", "装备与饰品"};
        float tabAreaH = panelH - 2 * pad;
        float tabH = tabAreaH / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            float ty = py + panelH - pad - (i + 1) * tabH;
            WoodUi.plank(batch, pixel, px + pad, ty + 3, sideW, tabH - 6, invTab == i);
            font.setColor(invTab == i ? Color.WHITE : new Color(0.8f, 0.76f, 0.62f, 1f));
            font.draw(batch, tabs[i], px + pad + sideW * 0.18f, ty + tabH / 2f + 6f);
            font.setColor(Color.WHITE);
        }
        if (invTab == 0) {
            drawBagTab(L);
        } else if (invTab == 1) {
            drawCraftTab(L);
        } else {
            drawEquipTab(L);
        }
        batch.end();
    }

    /** 面板统一布局（供绘制与点击共用）。 */
    private float[] invLayout() {
        float slot = uiCam.viewportHeight * 0.05f, gap = slot * 0.14f;
        float sideW = slot * 3.4f;
        int rows = (INV_TOTAL + INV_COLS - 1) / INV_COLS;
        float gridW = INV_COLS * slot + (INV_COLS - 1) * gap;
        float gridH = rows * slot + (rows - 1) * gap;
        float pad = slot * 0.5f;
        // 装备页竖向分组更高：3 组标题 + 9 格（天赋/技能、盔甲4、饰品3）
        float headH = slot * 0.85f;
        float equipH = 3 * (headH + gap) + 9 * (slot + gap);
        float contentH = invTab == 2 ? Math.max(gridH, equipH) : gridH;
        float panelW = sideW + gap + gridW + 2 * pad;
        float panelH = contentH + 2 * pad;
        float px = (uiCam.viewportWidth - panelW) / 2f, py = (uiCam.viewportHeight - panelH) / 2f;
        float cx0 = px + sideW + gap + pad, cy0 = py + panelH - pad;
        return new float[]{slot, gap, sideW, gridW, contentH, panelW, panelH, px, py, cx0, cy0, pad};
    }

    private float[] bagSlotRect(int i, float[] L) {
        float slot = L[0], gap = L[1], cx0 = L[9], cy0 = L[10];
        int r = i / INV_COLS, c = i % INV_COLS;
        return new float[]{cx0 + c * (slot + gap), cy0 - (r + 1) * slot - r * gap, slot};
    }

    private void drawBagTab(float[] L) {
        for (int i = 0; i < INV_TOTAL; i++) {
            float[] r = bagSlotRect(i, L);
            drawSlot(r[0], r[1], r[2], inventory.itemAt(i), inventory.countAt(i), i == inventory.selected());
        }
    }

    /** 装备页布局：竖向三组（职业/盔甲/饰品），每组标题 + 格子堆叠。cells=9 格矩形，heads=3 标题底 y。 */
    private void equipLayout(float[] L, float[][] cells, float[] heads) {
        float slot = L[0], gap = L[1], cx0 = L[9];
        float cellX = cx0 + (L[3] - slot) / 2f;                 // 单列居中于内容区
        float headH = slot * 0.85f;
        float y = L[10];
        int i = 0;
        int[] cnt = {2, 4, 3};
        for (int g = 0; g < 3; g++) {
            y -= headH; heads[g] = y; y -= gap;                 // 组标题（占 headH，heads[g]=其底）
            for (int k = 0; k < cnt[g]; k++) {
                cells[i][0] = cellX; cells[i][1] = y - slot; cells[i][2] = slot;
                y -= slot + gap; i++;
            }
        }
    }

    private void drawEquipTab(float[] L) {
        float[][] cells = new float[Equipment.SLOTS][3];
        float[] heads = new float[3];
        equipLayout(L, cells, heads);
        String[] groupTitle = {"职业", "盔甲", "饰品"};
        int[] counts = {2, 4, 3};
        float slot = L[0], headH = slot * 0.85f;
        int idx = 0;
        for (int g = 0; g < 3; g++) {
            font.setColor(1f, 0.9f, 0.55f, 1f);                 // 组标题（暖金色，在格子外）
            font.draw(batch, groupTitle[g], cells[idx][0], heads[g] + headH * 0.3f);
            font.setColor(Color.WHITE);
            for (int k = 0; k < counts[g]; k++, idx++) {         // 格内不放汉字
                drawSlot(cells[idx][0], cells[idx][1], cells[idx][2], equipment.get(idx), 1, false);
            }
        }
    }

    /** 合成页：每格仅产物图标（一行一格），悬停弹详情。 */
    private float[] craftRowRect(int i, float[] L) {
        float slot = L[0], gap = L[1], cx0 = L[9], cy0 = L[10];
        return new float[]{cx0, cy0 - (i + 1) * (slot + gap), slot, slot};
    }

    private void drawCraftTab(float[] L) {
        int shown = 0;
        float mx = uiMouseX(), my = uiMouseY();
        Recipe hover = null;
        for (Recipe rec : recipes) {
            if (!rec.unlocked) {
                continue;
            }
            float[] r = craftRowRect(shown, L);
            boolean ok = rec.canCraft(inventory);
            boolean over = mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
            WoodUi.plank(batch, pixel, r[0], r[1], r[2], r[3], ok || over);
            batch.setColor(1, 1, 1, ok ? 1f : 0.4f);
            batch.draw(itemSprite(rec.out), r[0] + r[2] * 0.1f, r[1] + r[3] * 0.1f, r[2] * 0.8f, r[3] * 0.8f);
            batch.setColor(1, 1, 1, 1);
            if (over) {
                hover = rec;
            }
            shown++;
        }
        if (hover != null) {
            drawCraftTooltip(hover, mx, my);
        }
    }

    /** 合成详情浮窗：产物名 + 逐材料“持有/需求”（够=绿、缺=红），靠近鼠标、不越屏。 */
    private void drawCraftTooltip(Recipe rec, float mx, float my) {
        float slot = uiCam.viewportHeight * 0.032f;
        float pad = slot * 0.6f, lineH = slot * 1.35f;
        float w = slot * 9f;
        float h = lineH * (rec.inIds.length + 1) + pad * 2f;
        float bx = mx + slot * 0.8f, by = my + lineH;          // 默认右上浮动
        if (bx + w > uiCam.viewportWidth) {
            bx = mx - w - slot * 0.8f;
        }
        if (by > uiCam.viewportHeight) {
            by = uiCam.viewportHeight;
        }
        if (by - h < 0) {
            by = h;
        }
        WoodUi.panel(batch, pixel, bx, by - h, w, h);
        float ty = by - pad - lineH * 0.7f;
        font.setColor(Color.WHITE);
        font.draw(batch, rec.out.cn(), bx + pad, ty);
        for (int k = 0; k < rec.inIds.length; k++) {
            Item in = Item.byId(rec.inIds[k]);
            int have = inventory.countOf(in);
            ty -= lineH;
            font.setColor(have >= rec.inCounts[k]
                    ? new Color(0.7f, 1f, 0.7f, 1f) : new Color(1f, 0.55f, 0.5f, 1f));
            font.draw(batch, in.cn() + "  " + have + "/" + rec.inCounts[k], bx + pad, ty);
        }
        font.setColor(Color.WHITE);
    }

    /** 画一个通用物品格（图标+数量+选中框）。 */
    private void drawSlot(float x, float y, float s, Item it, int count, boolean selected) {
        fill(x, y, s, s, 0f, 0f, 0f, 0.4f);
        if (it != null) {
            batch.setColor(1, 1, 1, 1);
            batch.draw(itemSprite(it), x + s * 0.09f, y + s * 0.09f, s * 0.82f, s * 0.82f);
            if (count > 1) {
                font.draw(batch, String.valueOf(count), x + s * 0.12f, y + s * 0.9f);
            }
        }
        if (selected) {
            batch.setColor(1f, 1f, 0.4f, 1f);
            border4(x, y, s, s);
            batch.setColor(1, 1, 1, 1);
        }
    }

    private void border4(float x, float y, float w, float h) {
        batch.draw(pixel, x - 1, y - 1, w + 2, 2f);
        batch.draw(pixel, x - 1, y + h - 1, w + 2, 2f);
        batch.draw(pixel, x - 1, y - 1, 2f, h + 2);
        batch.draw(pixel, x + w - 1, y - 1, 2f, h + 2);
    }

    /** 旧布局兼容（快捷栏命中仍用）：背包面板页 0 的格命中。 */
    private int invSlotAtMouse() {
        float[] L = invLayout();
        float mx = uiMouseX(), my = uiMouseY();
        if (invTab != 0) {
            return -1;
        }
        for (int i = 0; i < INV_TOTAL; i++) {
            float[] r = bagSlotRect(i, L);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[2]) {
                return i;
            }
        }
        return -1;
    }

    /** 背包面板内点击路由：标签切换 / 背包选格或装备 / 装备卸回 / 工作区合成。 */
    private void handleInventoryClick() {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        float[] L = invLayout();
        float mx = uiMouseX(), my = uiMouseY();
        float px = L[7], py = L[8], pad = L[11], sideW = L[2], panelH = L[6];
        // 标签
        float tabAreaH = panelH - 2 * pad, tabH = tabAreaH / 3f;
        for (int i = 0; i < 3; i++) {
            float ty = py + panelH - pad - (i + 1) * tabH;
            if (mx >= px + pad && mx <= px + pad + sideW && my >= ty && my <= ty + tabH) {
                invTab = i;
                return;
            }
        }
        if (invTab == 0) {
            int idx = invSlotAtMouse();
            if (idx >= 0) {
                inventory.select(idx);                          // 背包只选格（武器不再装备到栏→即手持）
            }
        } else if (invTab == 2) {
            float[][] cells = new float[Equipment.SLOTS][3];
            equipLayout(L, cells, new float[3]);
            for (int i = 0; i < Equipment.SLOTS; i++) {
                if (mx >= cells[i][0] && mx <= cells[i][0] + cells[i][2]
                        && my >= cells[i][1] && my <= cells[i][1] + cells[i][2]) {
                    Item got = equipment.unequip(i);            // 有护甲/饰品物品后可卸回（现皆空→无操作）
                    if (got != null && !inventory.add(got, 1)) {
                        equipment.equip(got);
                    }
                    return;
                }
            }
        } else {
            int shown = 0;
            for (Recipe rec : recipes) {
                if (!rec.unlocked) {
                    continue;
                }
                float[] r = craftRowRect(shown, L);
                if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                    if (rec.craft(inventory)) {
                        toast = "合成：" + rec.out.cn();
                        toastT = 1.5f;
                    }
                    return;
                }
                shown++;
            }
        }
    }

    /** 快捷栏尺寸（相对 UI 虚拟高）与命中检测，绘制与点击共用一套。 */
    private float hotbarSlot() {
        return uiCam.viewportHeight * 0.06f;
    }

    private float hotbarGap() {
        return hotbarSlot() * 0.12f;
    }

    private float hotbarX0() {
        return hotbarSlot() * 0.4f;
    }

    private float hotbarY0() {
        return hotbarSlot() * 0.4f;
    }

    /** 鼠标命中的快捷栏格（仅前 HOTBAR 格）；无则 -1。 */
    private int hotbarSlotAtMouse() {
        float s = hotbarSlot(), gap = hotbarGap(), x0 = hotbarX0(), y0 = hotbarY0();
        float mx = uiMouseX(), my = uiMouseY();
        int n = Math.min(HOTBAR, inventory.size());
        for (int i = 0; i < n; i++) {
            float x = x0 + i * (s + gap);
            if (mx >= x && mx <= x + s && my >= y0 && my <= y0 + s) {
                return i;
            }
        }
        return -1;
    }

    /** 小地图尺寸/位置（右上角，约占屏高 26%）。 */
    private float mmSize() {
        return uiCam.viewportHeight * 0.26f;
    }

    private float mmX() {
        return uiCam.viewportWidth - mmSize() - uiCam.viewportHeight * 0.02f;
    }

    private float mmY() {
        return uiCam.viewportHeight - mmSize() - uiCam.viewportHeight * 0.02f;
    }

    /** 小地图“−/+”切换按钮是否命中（开=左上“−”，闭=右上“+”）。 */
    private boolean minimapToggleHit() {
        float mx = uiMouseX(), my = uiMouseY();
        if (showMinimap) {
            float bs = uiCam.viewportHeight * 0.04f;
            float bx = mmX(), by = mmY() + mmSize() - bs;
            return mx >= bx && mx <= bx + bs && my >= by && my <= by + bs;
        }
        float s = uiCam.viewportHeight * 0.05f;
        float bx = uiCam.viewportWidth - s - uiCam.viewportHeight * 0.02f;
        float by = uiCam.viewportHeight - s - uiCam.viewportHeight * 0.02f;
        return mx >= bx && mx <= bx + s && my >= by && my <= by + s;
    }

    /** 左上仆从栏是否命中（仅通灵师且有仆从时可双击收起）。 */
    private boolean servantIndicatorHit() {
        if (playerClass != PlayerClass.SUMMONER || servants.isEmpty()) {
            return false;
        }
        float pip = uiCam.viewportHeight * 0.038f, step = pip * 1.12f;
        float x = pip * 0.6f, top = uiCam.viewportHeight - pip * 0.6f;
        float iy = (top - step * 1.5f) - step * 1.4f;
        float w = servants.size() * step + pip * 0.5f;
        float mx = uiMouseX(), my = uiMouseY();
        return mx >= x && mx <= x + w && my <= iy && my >= iy - pip;
    }

    /** 小地图：玩家周围地形色块 + 玩家黄点 + 怪红点；左上“−”可最小化。 */
    private void drawMinimap() {
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        if (!showMinimap) {
            float s = uiCam.viewportHeight * 0.05f;
            float bx = uiCam.viewportWidth - s - uiCam.viewportHeight * 0.02f;
            float by = uiCam.viewportHeight - s - uiCam.viewportHeight * 0.02f;
            WoodUi.plank(batch, pixel, bx, by, s, s, false);
            font.setColor(1, 1, 1, 1);
            font.draw(batch, "+", bx + s / 2f - 4f, by + s / 2f + 6f);
            batch.end();
            return;
        }
        float size = mmSize(), mx = mmX(), my = mmY();
        WoodUi.panel(batch, pixel, mx - 4, my - 4, size + 8, size + 8);
        final int R = 48;
        int cx = (int) Math.floor(player.centerX() / TILE);
        int cy = (int) Math.floor(player.centerY() / TILE);
        float cell = size / (2f * R);
        for (int dx = -R; dx <= R; dx++) {
            for (int dy = -R; dy <= R; dy++) {
                int tx = cx + dx, ty = cy + dy;
                if (!world.inBounds(tx, ty)) {
                    continue;
                }
                BlockType b = world.blockAt(tx, ty);
                if (b == BlockType.AIR) {
                    continue;
                }
                batch.setColor(b.r(), b.g(), b.b(), b == BlockType.WATER ? 0.7f : 1f);
                batch.draw(pixel, mx + (dx + R) * cell, my + (dy + R) * cell, cell + 0.5f, cell + 0.5f);
            }
        }
        for (Mob m : spawner.mobs()) {
            int mdx = (int) (m.centerX() / TILE) - cx, mdy = (int) (m.centerY() / TILE) - cy;
            if (Math.abs(mdx) <= R && Math.abs(mdy) <= R) {
                batch.setColor(1f, 0.25f, 0.25f, 1f);
                batch.draw(pixel, mx + (mdx + R) * cell - 1, my + (mdy + R) * cell - 1, cell + 2, cell + 2);
            }
        }
        batch.setColor(1f, 1f, 0.4f, 1f);                        // 玩家居中黄点
        batch.draw(pixel, mx + size / 2f - 2, my + size / 2f - 2, 4, 4);
        batch.setColor(1, 1, 1, 1);
        float bs = uiCam.viewportHeight * 0.04f;                 // 左上“−”最小化按钮
        WoodUi.plank(batch, pixel, mx, my + size - bs, bs, bs, false);
        font.setColor(1, 1, 1, 1);
        font.draw(batch, "-", mx + bs / 2f - 3f, my + size - bs / 2f + 5f);
        batch.end();
    }

    /** 快捷栏（屏幕左下，相对尺寸）：逐格画物品图标+数量，高亮当前格，旁标工具名。 */
    private void drawHotbar() {
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        int n = Math.min(HOTBAR, inventory.size());
        float slot = hotbarSlot(), gap = hotbarGap();
        float x0 = hotbarX0(), y0 = hotbarY0();
        WoodUi.panel(batch, pixel, x0 - 8, y0 - 8, n * (slot + gap) - gap + 16, slot + 16);   // 木质底板
        for (int i = 0; i < n; i++) {
            float x = x0 + i * (slot + gap);
            fill(x, y0, slot, slot, 0f, 0f, 0f, 0.42f);
            Item it = inventory.itemAt(i);
            if (it != null) {
                batch.setColor(1, 1, 1, 1);
                batch.draw(itemSprite(it), x + slot * 0.09f, y0 + slot * 0.09f, slot * 0.82f, slot * 0.82f);
                if (inventory.countAt(i) > 1) {
                    font.draw(batch, String.valueOf(inventory.countAt(i)), x + slot * 0.12f, y0 + slot * 0.9f);
                }
            }
            if (i == inventory.selected()) {
                batch.setColor(1f, 1f, 0.4f, 1f);
                float b = Math.max(2f, slot * 0.06f);
                batch.draw(pixel, x - 1, y0 - 1, slot + 2, b);
                batch.draw(pixel, x - 1, y0 + slot - b + 1, slot + 2, b);
                batch.draw(pixel, x - 1, y0 - 1, b, slot + 2);
                batch.draw(pixel, x + slot - b + 1, y0 - 1, b, slot + 2);
                batch.setColor(1, 1, 1, 1);
            }
        }
        font.setColor(0.85f, 0.88f, 1f, 1f);
        font.draw(batch, "工具：" + tool.name() + "（力度" + tool.power() + "x）", x0, y0 + slot + 20);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    private void drawCursor() {
        int[] tile = mouseTile();
        if (tile != null) {
            batch.setColor(1f, 1f, 0.4f, 0.35f);
            batch.draw(pixel, tile[0] * (float) TILE, tile[1] * (float) TILE, TILE, TILE);
            batch.setColor(1, 1, 1, 1);
        }
    }

    private void drawHud() {
        BitmapFont sf = CjkFonts.get(11);          // 小字，不占视野
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        String[] lines = {
                "L1 浅梦",
                "时刻 " + clock.format(),
                "天光 " + clock.skyLevel1to16() + "/16",
                "帧率 " + Gdx.graphics.getFramesPerSecond(),
        };
        float lineH = uiCam.viewportHeight * 0.026f;
        float margin = uiCam.viewportHeight * 0.02f;
        float right = uiCam.viewportWidth - margin;               // 右边缘对齐
        float y = uiCam.viewportHeight / 2f + lines.length * lineH / 2f;   // 竖向居中
        sf.setColor(0.85f, 0.88f, 1f, 0.7f);
        for (String ln : lines) {
            y -= lineH;
            sf.draw(batch, ln, right - new GlyphLayout(sf, ln).width, y);
        }
        sf.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** 设置存档槽位（新游戏分配新档时用）。 */
    public void setSlot(String slot) {
        this.slot = slot;
    }

    /** 导出当前世界为存档数据（改动 diff + 玩家状态）。 */
    public GameSave toSave() {
        GameSave s = new GameSave();
        s.slot = slot;
        s.className = playerClass.name();
        s.seed = seed;
        s.editIdx = new int[edits.size()];
        s.editBlock = new int[edits.size()];
        int i = 0;
        for (java.util.Map.Entry<Integer, Integer> e : edits.entrySet()) {
            s.editIdx[i] = e.getKey();
            s.editBlock[i] = e.getValue();
            i++;
        }
        s.playerX = player.x();
        s.playerY = player.y();
        s.invItem = inventory.itemIdSnapshot();
        s.invCount = inventory.countSnapshot();
        s.invSelected = inventory.selected();
        s.lucidity = stats.lucidity();
        s.maxLucidity = stats.maxLucidity();
        s.mana = stats.mana();
        s.maxMana = stats.maxMana();
        s.clockMinutes = clock.totalMinutes();
        s.coins = coins;
        s.servantCap = servantCap;
        s.faceTemplateId = appearance.templateId;
        s.faceColors = appearance.colors.clone();
        return s;
    }

    /** 应用存档：重放世界改动 + 恢复玩家/背包/双条/时刻。 */
    public void applySave(GameSave s) {
        if (s.slot != null) {
            this.slot = s.slot;
        }
        edits.clear();
        if (s.editIdx != null && s.editBlock != null) {
            int w = world.getWidth();
            for (int i = 0; i < s.editIdx.length && i < s.editBlock.length; i++) {
                int idx = s.editIdx[i], b = s.editBlock[i];
                world.setBlock(idx % w, idx / w, BlockType.of((short) b));
                edits.put(idx, b);
            }
        }
        player.setPos(s.playerX, s.playerY);
        inventory.loadFrom(s.invItem, s.invCount, s.invSelected);
        stats.setLucidity(s.lucidity);
        stats.setMana(s.mana);
        clock.setTotalMinutes(s.clockMinutes);
        coins = s.coins;
        if (s.servantCap > 0) {
            servantCap = s.servantCap;   // 存上限；仆从不存，重进需重新召唤
        }
        servants.clear();
        if (s.faceColors != null && s.faceColors.length == PaintedLook.W * PaintedLook.H) {
            appearance = new PaintedLook(s.faceTemplateId, s.faceColors);
            buildDreamer();                     // 读档按存档的上色重建外观纹理
        }
        lightDirty = true;
    }

    /** 由捏脸上色数组生成/重建小人纹理（读档或切换外观后调用）。 */
    private void buildDreamer() {
        Pixmap pm = lookPixmap(appearance.colors);
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        if (dreamerTex != null) {
            dreamerTex.dispose();
        }
        dreamerTex = t;
        dreamerRegion = new TextureRegion(t);
    }

    private static Pixmap lookPixmap(int[] colors) {
        Pixmap pm = new Pixmap(PaintedLook.W, PaintedLook.H, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        for (int i = 0; i < colors.length; i++) {
            int v = colors[i];
            if ((v & 0xFFFFFF) == 0) {          // 纯黑=透明键→不画（抠图，黑区删除）
                continue;
            }
            pm.setColor(((v >> 16) & 0xFF) / 255f, ((v >> 8) & 0xFF) / 255f, (v & 0xFF) / 255f,
                    ((v >>> 24) & 0xFF) / 255f);
            pm.drawPixel(i % PaintedLook.W, i / PaintedLook.W);
        }
        return pm;
    }

    @Override
    public void dispose() {
        batch.dispose();
        pixel.dispose();
        textures.dispose();
        uiIcons.dispose();
        sky.dispose();
        if (dreamerTex != null) {
            dreamerTex.dispose();
        }
    }
}
