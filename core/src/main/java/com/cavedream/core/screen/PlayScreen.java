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
import com.cavedream.core.world.mob.Bat;
import com.cavedream.core.world.mob.Mob;
import com.cavedream.core.world.mob.Servant;
import com.cavedream.core.world.mob.Slime;
import com.cavedream.core.world.mob.SpawnManager;
import com.cavedream.core.world.gen.VillageBuilder;
import com.cavedream.core.world.npc.GuideNpc;
import com.cavedream.core.anim.BoneId;
import com.cavedream.core.anim.Pose;
import com.cavedream.core.render.SkeletalAvatar;

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
    private static final float PICKUP_MAGNET_PX = 5f * TILE;    // 拾取吸附半径（初始 5 格）
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
    private final GuideNpc guide = new GuideNpc();                          // 构梦者（商人 NPC）
    private Texture guideTex;                                               // 构梦者外观纹理
    private TextureRegion guideRegion;
    private boolean shopOpen;                                               // 商店面板开合
    private boolean codexMenuOpen;                                          // 法典菜单开合（按 X）
    private int codexView;                                                  // 法典视图：0主菜单 1 NPC列表 2 物品列表
    private final java.util.Map<Integer, PaintedLook> itemPaints = new java.util.HashMap<>();   // itemId→重绘外观
    private final java.util.Map<Integer, Texture> itemPaintTex = new java.util.HashMap<>();     // 重绘外观的缓存纹理
    private final java.util.HashMap<Integer, Inventory> chests = new java.util.HashMap<>();   // 木箱内容 tileIdx→背包
    private int openChest = -1;                                             // 打开的容器 tileIdx，-1 无
    private int openFurnace = -1;                                          // 打开的熔炉锚点 tileIdx，-1 无
    private final java.util.HashMap<Integer, FurnaceData> furnaces = new java.util.HashMap<>();   // 熔炉锚点→料/产物
    private static final int FURN_SLOTS = 4;                               // 熔炉 4 对“矿→锭”并行槽
    private final java.util.HashMap<Integer, Integer> mountEdits = new java.util.HashMap<>();   // 墙面对象改动 idx→blockId（持久）
    private final java.util.HashMap<Integer, TreeData> trees = new java.util.HashMap<>();      // 锚点idx→树（渲染/砍伐）
    private final java.util.HashMap<Integer, GrowState> growing = new java.util.HashMap<>();   // 锚点idx→未长成树生长态
    private static final float TREE_GROW_SECONDS = 20f;   // 每 +1 格高的间隔（可配）
    private volatile String guideLine = "";                                 // 构梦者台词（大模型生成）
    private float guideEngageT;                                             // 与构梦者相处计时（≥3s 触发闲聊）
    private volatile boolean chatterLoading;                                // 闲聊生成中（避免并发）
    private volatile boolean lineLoading;
    private final java.util.List<String> storyLog = new java.util.ArrayList<>();   // 本存档“剧情记忆”（随每次交互增长）
    private TextureRegion blobRegion;
    private TextureRegion pickaxeRegion;
    private TextureRegion coinRegion;
    private TextureRegion treeRegion;
    // —— 骨骼蒙皮角色 ——
    private SkeletalAvatar avatar;
    private final Pose avatarPose = new Pose();
    private float avatarPhase;
    private float pvBob, pvTilt, pvW, pvH;
    private int pvFacing = 1;
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
    private int craftScroll;                                                  // 合成页网格滚动（顶格索引）
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
    private String saveName = "";                                             // 存档显示名（创建命名/列表改名）
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
        avatar = new SkeletalAvatar();
        avatar.setAppearance(appearance);
        pickaxeRegion = new TextureRegion(textures.pickaxe());
        coinRegion = new TextureRegion(textures.coin());
        treeRegion = new TextureRegion(textures.tree());
        int[] gs = VillageBuilder.build(world, spawnTileX, spawnTileY, seed);   // 出生点旁建村庄，得向导落点
        guide.place(gs[0] * (float) TILE, gs[1] * (float) TILE);
        guide.setHome(gs[0] * (float) TILE, TILE * 3f);   // 以落点为中心、左右各 3 格自由走动
        guideTex = new Texture(lookPixmap(guide.look().colors));
        guideTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        guideRegion = new TextureRegion(guideTex);
        blobRegion = new TextureRegion(textures.cornerBlob());
        blobWhiteRegion.setRegion(new TextureRegion(textures.blobWhite()));
        for (int i = 0; i < 256; i++) {
            skyRows[i] = new TextureRegion(textures.skyGrad(), 0, i, 8, 8);
            depthRows[i] = new TextureRegion(textures.depthGrad(), 0, i, 8, 8);
        }
        lightEngine.recomputeRegion(world, spawnTileX, spawnTileY, LIGHT_RADIUS);   // 初始局部光照（围绕出生点）
        lastLCx = spawnTileX;
        lastLCy = spawnTileY;
        rebuildTreeRegistry();   // 扫描世界登记已有树（世界树 + 玩家树）
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
        game.setCurrentPlay(this);   // 登记为当前世界屏（供法典画板重绘后返回）
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                && !showInv && !shopOpen && !codexMenuOpen && openChest < 0 && openFurnace < 0) {
            paused = !paused;   // 无面板时才用 ESC 弹暂停菜单（面板开着时 ESC 由面板自己处理）
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
                    keyDown(Input.Keys.S) || keyDown(Input.Keys.DOWN),
                    delta);
            if (player.isMoving() && player.isOnGround()) {
                walkPhase += delta * 11f;
            } else {
                walkPhase = 0f;
            }
            int fallDmg = player.consumeFallDamage();   // 摔落伤害（开局无保护→高空坠落可致命）
            if (fallDmg > 0) {
                stats.damage(fallDmg);
                toast = "摔落受伤 −" + fallDmg;
                toastT = 1.6f;
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
            updateGuide();
            updateGuideNpc(delta);
            updateTrees(delta);
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
        drawTrees();
        drawMiningProgress();
        drawLighting();
        drawMobs();
        drawGuide();
        drawDrops();
        batch.end();
        updatePlayerVisual();
        if (avatar != null) {
            avatar.draw(camera, player.x(), player.y() + pvBob, pvW, pvH, pvFacing, pvTilt);
        }
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
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
        drawShop();
        drawCodexMenu();
        drawChest();
        drawFurnace();
        drawToast();
        drawGuideBubble();
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
        // 滚轮：合成页且鼠标在列表区内→翻列表；面板开着不滚快捷栏；都没开才切快捷栏
        int hcnt = Math.min(HOTBAR, inventory.size());
        if (wheelAccum != 0f) {
            if (showInv && invTab == 1 && craftAreaHover()) {
                craftScroll += (wheelAccum > 0 ? 1 : -1) * INV_COLS;
                int total = unlockedRecipes().size();
                int maxScroll = Math.max(0, total - craftVisibleCount(invLayout()));
                if (craftScroll > maxScroll) {
                    craftScroll = maxScroll;
                }
                if (craftScroll < 0) {
                    craftScroll = 0;
                }
                wheelAccum = 0f;
            } else if (showInv || shopOpen || codexMenuOpen || openChest >= 0 || openFurnace >= 0) {
                wheelAccum = 0f;   // 任何面板开着：滚轮不切快捷栏
            } else {
                while (wheelAccum >= 1f) {
                    inventory.select((inventory.selected() + 1) % hcnt);
                    wheelAccum -= 1f;
                }
                while (wheelAccum <= -1f) {
                    inventory.select((inventory.selected() - 1 + hcnt) % hcnt);
                    wheelAccum += 1f;
                }
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            showInv = !showInv;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.Q)) {
            showMinimap = !showMinimap;   // Q 开关小地图
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.X)) {        // X 随处开/关法典（需已购得），独立于背包
            if (guide.codexOwned()) {
                codexMenuOpen = !codexMenuOpen;
                codexView = 0;
            } else {
                toast = "尚未拥有《世界准则法典》（去构梦者商店）";
                toastT = 1.5f;
            }
            return;   // 本次 X 只做开合，避免同帧又被 handleCodexClick 处理
        }
        if (codexMenuOpen) {                 // 法典优先（可随处 X 打开，独立于背包）
            miningNow = false;
            digProgress = 0f;
            handleCodexClick();
            return;
        }
        if (shopOpen) {                      // 商店开启：只处理商店点击
            miningNow = false;
            digProgress = 0f;
            handleShopClick();
            return;
        }
        if (showInv) {                       // 背包开启：只处理面板内点击，不挖掘/放置/攻击
            miningNow = false;
            digProgress = 0f;
            handleInventoryClick();
            return;
        }
        if (openChest >= 0) {                // 木箱开启：只处理存取
            miningNow = false;
            digProgress = 0f;
            handleChestClick();
            return;
        }
        if (openFurnace >= 0) {              // 熔炉开启：只处理存料/取锭/熔炼
            miningNow = false;
            digProgress = 0f;
            handleFurnaceClick();
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
            if (b.isTree() && !isTreeChopCell(tile[0], tile[1])) {
                need = -1f;   // 树仅底部中间格可砍（镐也砍不动，见 Tool 门控）
            }
            if (need >= 0f) {
                if (tile[0] != digX || tile[1] != digY) {
                    digX = tile[0];
                    digY = tile[1];
                    digProgress = 0f;
                }
                digProgress += frameDelta / Math.max(0.03f, need);
                mining = true;
                if (digProgress >= 1f) {
                    if (b.isTree()) {
                        chopTree(tile[0], tile[1]);   // 整棵消散→掉木材
                    } else if (world.mountAt(tile[0], tile[1]) != BlockType.AIR) {
                        popMount(tile[0], tile[1]);   // 先摘墙面对象，不伤墙
                    } else {
                        breakBlock(tile[0], tile[1], b);   // 多格家具整体破坏、掉 1
                    }
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

        // 右键熔炉→打开熔铸界面（自带存储+加工：上排放矿石、自动熔为下排金属锭）
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && tile != null
                && world.blockAt(tile[0], tile[1]).isSmelter()) {
            int[] a = anchorOf(tile[0], tile[1], BlockType.FURNACE);
            openFurnace = a[1] * world.getWidth() + a[0];
            smeltTick(furnaceAt(openFurnace));
            return;
        }
        // 右键存储摆件→打开存取（多格箱→锁到锚点格）
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && tile != null
                && world.blockAt(tile[0], tile[1]).storageCapacity() > 0) {
            int[] a = anchorOf(tile[0], tile[1], world.blockAt(tile[0], tile[1]));
            openChest = a[1] * world.getWidth() + a[0];
            return;
        }
        // 右键：靠近构梦者则打开商店（否则放置方块）
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && guide.present() && nearGuide()) {
            shopOpen = true;
            askGuideLine();
            return;
        }
        // 右键：放置方块（支持多格家具）/ 交互
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && tile != null
                && held != null && held.placeable()) {
            if (held.block().isTree()) {
                plantTree(tile[0], tile[1]);   // 树苗→占 3×起始高、随后逐格长高
            } else if (!tryMount(tile[0], tile[1], held.block())) {
                placeBlock(tile[0], tile[1], held.block());
            }
        }
    }

    /** 快捷格序号 0~9 对应数字键：0→NUM_1 … 8→NUM_9、 9→NUM_0。 */
    private static int numKey(int i) {
        return i < 9 ? Input.Keys.NUM_1 + i : Input.Keys.NUM_0;
    }

    /* ---------------- 构梦者（商人 NPC）与商店 ---------------- */

    /** 铸梦币达阈值→构梦者出现在村庄周围（捏脸剧情触发留后续）。 */
    private void updateGuide() {
        if (!guide.present() && GuideNpc.reachesSpawn(coins)) {
            guide.setPresent(true);
            logStory("铸梦币渐盈，构梦者在村庄现身");
            toast = "构梦者在村庄出现了（走近右键对话）";
            toastT = 3f;
        }
    }

    /** 构梦者自由走动（贴地巡逻）+ 与玩家相处≥3s 周期性冒语言泡。 */
    private void updateGuideNpc(float dt) {
        if (!guide.present()) {
            return;
        }
        guide.wander(dt);
        // 贴地：从脚下向下找第一格实心/平台
        int tx = (int) Math.floor((guide.x() + TILE * 0.65f) / TILE);
        int ty = (int) Math.floor((guide.y() + 2f) / TILE);
        int ground = -1;
        for (int y = ty; y >= ty - 6 && y >= 0; y--) {
            if (world.blockAt(tx, y).solid() || world.isPlatform(tx, y)) {
                ground = y;
                break;
            }
        }
        if (ground >= 0) {
            guide.place(guide.x(), (ground + 1) * (float) TILE);
        }
        guideEngageT = nearGuide() ? guideEngageT + dt : 0f;
        if (guide.tickChat(dt, guideEngageT >= 3f)) {
            askGuideChatter();
        }
    }

    private static final String[] GUIDE_IDLE = {
            "今天梦里也很安静呢。", "你见过会发光的鱼吗？", "构梦，就是织一场醒不来的梦。",
            "这村庄的风，带着一点旧记忆。", "要是累了，就坐下歇会儿吧。", "传说深处有扇门，通往没做过的梦。",
            "铸梦币叮当作响，那是梦的心跳。", "别怕黑，黑里才看得见星。",
    };

    /** 构梦者主动闲聊：优先大模型异步生成，离线直接随机梦呓。 */
    private void askGuideChatter() {
        final String fb = GUIDE_IDLE[(int) (Math.random() * GUIDE_IDLE.length)];
        if (game.npcDialogue() == null || !game.npcDialogue().available() || chatterLoading) {
            guide.say(fb, 4.5f);   // 离线/生成中：直接随机梦呓
            return;
        }
        chatterLoading = true;
        final boolean owned = guide.codexOwned();
        final String mem = storyMemory();
        new Thread(() -> {
            String line = game.npcDialogue().reply(
                    game.npcDialogue().guidePersona(owned, mem),
                    "（你正闲逛，随口说一句轻松的梦呓，不超过两句。）", fb);
            guide.say(line, 4.5f);
            chatterLoading = false;
        }, "cavedream-chatter").start();
    }

    /** 构梦者头顶语言泡（世界坐标→UI 屏幕绘制）。 */
    private void drawGuideBubble() {
        if (!guide.present() || !guide.talking()) {
            return;
        }
        com.badlogic.gdx.math.Vector3 p = new com.badlogic.gdx.math.Vector3(
                guide.x() + TILE * 0.65f, guide.y() + TILE * 2.7f, 0);
        camera.project(p);   // 世界→屏幕真实像素（y 自底）
        float sx = p.x * (uiCam.viewportWidth / Gdx.graphics.getWidth());
        float sy = p.y * (uiCam.viewportHeight / Gdx.graphics.getHeight());
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        String txt = guide.bubble();
        float maxW = uiCam.viewportWidth * 0.34f;
        com.badlogic.gdx.graphics.g2d.GlyphLayout gl = new com.badlogic.gdx.graphics.g2d.GlyphLayout(
                font, txt, Color.WHITE, maxW, com.badlogic.gdx.utils.Align.center, true);
        float bw = gl.width + 20f, bh = gl.height + 16f;
        float bx = clamp(sx - bw / 2f, 6f, uiCam.viewportWidth - bw - 6f);
        float by = clamp(sy, 6f, uiCam.viewportHeight - bh - 6f);
        WoodUi.panel(batch, pixel, bx, by, bw, bh);
        font.setColor(Color.WHITE);
        font.draw(batch, gl, bx + 10f, by + bh - 9f);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** 玩家是否在构梦者交互范围内。 */
    private boolean nearGuide() {
        if (!guide.present()) {
            return false;
        }
        float gx = guide.x() + TILE * 0.65f, gy = guide.y() + TILE * 1.3f;
        return Math.hypot(gx - player.centerX(), gy - player.centerY()) < TILE * 3.5f;
    }

    /** 构梦者：世界坐标绘制（用其捣脸外观纹理）。 */
    private void drawGuide() {
        if (!guide.present()) {
            return;
        }
        batch.setColor(1, 1, 1, 1);
        float gw = TILE * 1.3f, gh = TILE * 2.6f;
        if (guide.facing() < 0f) {   // 朝左→水平镜像（底模默认朝右）
            batch.draw(guideRegion, guide.x() + gw, guide.y(), 0f, 0f, gw, gh, -1f, 1f, 0f);
        } else {
            batch.draw(guideRegion, guide.x(), guide.y(), gw, gh);
        }
    }

    private float[] shopRect() {
        float w = uiCam.viewportWidth * 0.4f, h = uiCam.viewportHeight * 0.36f;
        return new float[]{uiCam.viewportWidth / 2f - w / 2f, uiCam.viewportHeight / 2f - h / 2f, w, h};
    }

    private float[] shopBuyRect() {
        float[] s = shopRect();
        float iy = s[1] + s[3] - 84f;
        return new float[]{s[0] + 16f, iy - 64f, s[2] - 32f, 44f};
    }

    /** 商店面板：世界准则法典（50 币、一次性）。 */
    private void drawShop() {
        if (!shopOpen) {
            return;
        }
        float[] s = shopRect();
        float x = s[0], y = s[1], w = s[2], h = s[3];
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        WoodUi.panel(batch, pixel, x, y, w, h);
        font.setColor(0.95f, 0.9f, 0.7f, 1f);
        font.draw(batch, "构梦者 · 商店", x + 16, y + h - 26);
        font.setColor(1f, 0.9f, 0.4f, 1f);
        font.draw(batch, "铸梦币 " + coins, x + w - 150, y + h - 26);
        font.setColor(0.85f, 0.9f, 1f, 1f);
        font.draw(batch, clip(lineLoading ? "……" : guideLine, 26), x + 16, y + h - 52);   // 构梦者台词
        font.setColor(1, 1, 1, 1);
        float iy = y + h - 84;
        font.setColor(Color.WHITE);
        font.draw(batch, "世界准则法典", x + 16, iy);
        float[] b = shopBuyRect();
        boolean owned = guide.codexOwned();
        boolean afford = guide.canSellCodex(coins);
        WoodUi.plank(batch, pixel, b[0], b[1], b[2], b[3], afford && !owned);
        font.setColor(owned ? new Color(0.5f, 0.85f, 0.5f, 1f) : Color.WHITE);
        font.draw(batch, owned ? "已购得" : (afford ? "购买 · 50 铸梦币" : "需 50 铸梦币"),
                b[0] + b[2] / 2f - 60, b[1] + b[3] / 2f + 6);
        font.setColor(0.7f, 0.72f, 0.85f, 1f);
        font.draw(batch, "右键 / Esc 关闭", x + 16, y + 26);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** 异步向大模型要一句构梦者台词（无 key/失败→离线兵底）；不阻塞渲染。 */
    private void askGuideLine() {
        if (lineLoading) {
            return;
        }
        lineLoading = true;
        final boolean owned = guide.codexOwned();
        final String persona = game.npcDialogue().guidePersona(owned, storyMemory());   // 把“这个梦的记忆”喂给大模型
        final String ctx = owned ? "玩家再次来到你面前，想闲聊几句。" : "玩家第一次走近你，向你打招呼。";
        logStory("与构梦者交谈");   // 每次大模型调用→推进剧情记忆（不改主线）
        new Thread(() -> {
            guideLine = game.npcDialogue().reply(persona, ctx, "……既然来了，看看我的货？");
            lineLoading = false;
        }, "cavedream-dialogue").start();
    }

    /** 追加一条剧情记忆（连续去重、限量保留最近 60 条）。 */
    private void logStory(String beat) {
        if (beat == null || beat.isBlank()) {
            return;
        }
        int n = storyLog.size();
        if (n > 0 && beat.equals(storyLog.get(n - 1))) {
            return;
        }
        storyLog.add(beat);
        if (storyLog.size() > 60) {
            storyLog.remove(0);
        }
    }

    /** 取最近若干条作为大模型的“经历记忆”上下文。 */
    private String storyMemory() {
        int from = Math.max(0, storyLog.size() - 12);
        return String.join("；", storyLog.subList(from, storyLog.size()));
    }

    /** block id 是否在合法范围（读档防未知 id 使 BlockType.of 抛异常）。 */
    private static boolean isValidBlock(int id) {
        return id >= 0 && id < BlockType.values().length;
    }

    private static int[] slice(int[] src, int from, int len) {
        int[] out = new int[len];
        java.util.Arrays.fill(out, Inventory.EMPTY);
        for (int i = 0; i < len; i++) {
            int k = from + i;
            out[i] = (src != null && k >= 0 && k < src.length) ? src[k] : Inventory.EMPTY;
        }
        return out;
    }

    private static String clip(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    /** 法典菜单：选“重塑样貌”→开重绘画板；方块/武器画板暂“开发中”。 */
    private float[] codexRect() {
        float w = uiCam.viewportWidth * 0.44f, h = uiCam.viewportHeight * 0.42f;
        return new float[]{uiCam.viewportWidth / 2f - w / 2f, uiCam.viewportHeight / 2f - h / 2f, w, h};
    }

    private float[] codexRowRect(int i) {
        float[] c = codexRect();
        float rh = c[3] * 0.2f;
        float top = c[1] + c[3] - 56f;
        return new float[]{c[0] + 16f, top - (i + 1) * rh, c[2] - 32f, rh - 8f};
    }

    private void drawCodexMenu() {
        if (!codexMenuOpen) {
            return;
        }
        float[] c = codexRect();
        float x = c[0], y = c[1], w = c[2], h = c[3];
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        WoodUi.panel(batch, pixel, x, y, w, h);
        font.setColor(0.95f, 0.9f, 0.7f, 1f);
        font.draw(batch, codexView == 0 ? "世界准则法典 · 造梦画板"
                : codexView == 1 ? "选择要重塑的 NPC" : "选择要重塑的物品", x + 16, y + h - 26);
        font.setColor(1, 1, 1, 1);
        float mx = uiMouseX(), my = uiMouseY();
        if (codexView == 0) {
            String[] rows = {"修改已有 NPC 外观", "修改已有物品外观"};
            for (int i = 0; i < rows.length; i++) {
                float[] r = codexRowRect(i);
                boolean hov = mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3];
                WoodUi.plank(batch, pixel, r[0], r[1], r[2], r[3], hov);
                font.setColor(Color.WHITE);
                font.draw(batch, rows[i], r[0] + 14, r[1] + r[3] / 2f + 6);
                font.setColor(1, 1, 1, 1);
            }
        } else if (codexView == 1) {
            drawNpcPicker(mx, my);
        } else {
            drawItemPicker(mx, my);
        }
        font.setColor(0.7f, 0.72f, 0.85f, 1f);
        font.draw(batch, codexView == 0 ? "X / Esc 关闭" : "Esc 返回列表", x + 16, y + 24);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** NPC 选择列表（当前：构梦者）：头像格 + 悬浮名。 */
    private void drawNpcPicker(float mx, float my) {
        String[] names = npcNames();
        float slot = uiCam.viewportHeight * 0.09f;
        float[] c = codexRect();
        float left = c[0] + 24, top = c[1] + c[3] - 56;
        for (int i = 0; i < names.length; i++) {
            float cx = left + i * (slot + 12), cy = top - slot;
            boolean hov = mx >= cx && mx <= cx + slot && my >= cy && my <= cy + slot;
            WoodUi.plank(batch, pixel, cx, cy, slot, slot, hov);
            batch.draw(guideRegion, cx + 4, cy + 4, slot - 8, slot - 8);
            font.setColor(Color.WHITE);
            font.draw(batch, names[i], cx, cy - 12);
            font.setColor(1, 1, 1, 1);
            if (hov) {
                codexTooltip(names[i], "点击重塑它的样貌", mx, my);
            }
        }
    }

    /** 物品选择列表（网格 + 悬浮名）：点一个→重绘其外观。 */
    private void drawItemPicker(float mx, float my) {
        java.util.List<Item> items = paintableItems();
        float slot = uiCam.viewportHeight * 0.06f, gap = slot * 0.16f;
        int cols = 6;
        float[] c = codexRect();
        float left = c[0] + 24, top = c[1] + c[3] - 52;
        for (int i = 0; i < items.size(); i++) {
            int r = i / cols, col = i % cols;
            float cx = left + col * (slot + gap), cy = top - (r + 1) * slot - r * gap;
            Item it = items.get(i);
            boolean hov = mx >= cx && mx <= cx + slot && my >= cy && my <= cy + slot;
            fill(cx, cy, slot, slot, 0f, 0f, 0f, 0.4f);
            batch.setColor(1, 1, 1, 1);
            batch.draw(itemSprite(it), cx + slot * 0.1f, cy + slot * 0.1f, slot * 0.8f, slot * 0.8f);
            if (itemPaints.containsKey(it.id())) {
                batch.setColor(0.4f, 1f, 0.5f, 1f);   // 已重绘→绿角标
                batch.draw(pixel, cx + slot - 5, cy + slot - 5, 5, 5);
                batch.setColor(1, 1, 1, 1);
            }
            if (hov) {
                codexTooltip(it.cn(), itemPaints.containsKey(it.id()) ? "已重塑·点击再改" : "点击重塑外观", mx, my);
            }
        }
    }

    /** 选择器通用悬浮小窗（标题 + 一行说明）。 */
    private void codexTooltip(String title, String hint, float mx, float my) {
        float w = uiCam.viewportHeight * 0.24f, hh = uiCam.viewportHeight * 0.08f;
        float bx = mx + 12, by = my + 8;
        if (bx + w > uiCam.viewportWidth) {
            bx = mx - w - 12;
        }
        if (by + hh > uiCam.viewportHeight) {
            by = uiCam.viewportHeight - hh;
        }
        WoodUi.panel(batch, pixel, bx, by, w, hh);
        font.setColor(Color.WHITE);
        font.draw(batch, title, bx + 8, by + hh * 0.66f);
        font.setColor(0.7f, 0.72f, 0.85f, 1f);
        font.draw(batch, hint, bx + 8, by + hh * 0.24f);
        font.setColor(1, 1, 1, 1);
    }

    /** 可重绘物品：所有可放置方块 + 五职业武器 + 代表工具。 */
    private java.util.List<Item> paintableItems() {
        java.util.List<Item> list = new java.util.ArrayList<>();
        for (BlockType b : BlockType.values()) {
            if (b != BlockType.AIR && b != BlockType.FOG && b != BlockType.WATER && b != BlockType.TREE) {
                list.add(Item.ofBlock(b));
            }
        }
        for (int id : new int[]{200, 201, 202, 203, 204, 100, 110}) {
            list.add(Item.byId(id));
        }
        return list;
    }

    /** 可重绘 NPC 名单（当前：构梦者）。 */
    private String[] npcNames() {
        return new String[]{"构梦者"};
    }

    private void handleCodexClick() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (codexView == 0) {
                codexMenuOpen = false;
            } else {
                codexView = 0;
            }
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.X) && codexView == 0) {
            codexMenuOpen = false;
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        float mx = uiMouseX(), my = uiMouseY();
        if (codexView == 0) {
            for (int i = 0; i < 2; i++) {
                float[] r = codexRowRect(i);
                if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
                    codexView = i + 1;
                    return;
                }
            }
        } else if (codexView == 1) {
            String[] names = npcNames();
            float slot = uiCam.viewportHeight * 0.09f;
            float[] c = codexRect();
            float left = c[0] + 24, top = c[1] + c[3] - 56;
            for (int i = 0; i < names.length; i++) {
                float cx = left + i * (slot + 12), cy = top - slot;
                if (mx >= cx && mx <= cx + slot && my >= cy && my <= cy + slot) {
                    codexMenuOpen = false;
                    codexView = 0;
                    game.openPaint(guide.look(), "重塑 · " + names[i], this::applyGuideRepaint);
                    return;
                }
            }
        } else {
            java.util.List<Item> items = paintableItems();
            float slot = uiCam.viewportHeight * 0.06f, gap = slot * 0.16f;
            int cols = 6;
            float[] c = codexRect();
            float left = c[0] + 24, top = c[1] + c[3] - 52;
            for (int i = 0; i < items.size(); i++) {
                int r = i / cols, col = i % cols;
                float cx = left + col * (slot + gap), cy = top - (r + 1) * slot - r * gap;
                if (mx >= cx && mx <= cx + slot && my >= cy && my <= cy + slot) {
                    Item it = items.get(i);
                    PaintedLook seed = itemPaints.get(it.id());
                    if (seed == null) {
                        seed = defaultItemLook(it);
                    }
                    final int id = it.id();
                    codexMenuOpen = false;
                    codexView = 0;
                    game.openPaint(seed, "重塑 · " + it.cn(), look -> applyItemPaint(id, look));
                    return;
                }
            }
        }
    }

    /** 应用物品重绘：存 PaintedLook + 重建缓存纹理（下次 itemSprite 优先用它）。 */
    private void applyItemPaint(int itemId, PaintedLook look) {
        itemPaints.put(itemId, look);
        Texture old = itemPaintTex.remove(itemId);
        if (old != null) {
            old.dispose();
        }
        Pixmap pm = lookPixmap(look.w, look.h, look.colors);
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        pm.dispose();
        itemPaintTex.put(itemId, t);
        toast = "物品外观已重塑";
        toastT = 2f;
    }

    /** 物品默认外观画布：取该物品自身贴图（方块/币 16×16、武器/工具 24×24）作起始。 */
    private PaintedLook defaultItemLook(Item it) {
        if (it.kind() == Item.Kind.COIN) {
            return PaintedLook.forItem(16, 16, BlockTextures.coinArgb());
        }
        if (it.placeable()) {
            return PaintedLook.forItem(16, 16, BlockTextures.blockArgb(it.block()));
        }
        int[] a = UiIcons.iconArgb(it);
        if (a != null) {
            return PaintedLook.forItem(UiIcons.SIZE, UiIcons.SIZE, a);
        }
        return PaintedLook.forItem(16, 16, BlockTextures.blockArgb(BlockType.WOOD));
    }

    /** 重绘完成：应用新外观并重建小人纹理（在 GL 线程回调）。 */
    private void applyRepaint(PaintedLook look) {
        this.appearance = look;
        buildDreamer();
        logStory("以法典重塑了自己的样貌");
        toast = "样貌已重塑";
        toastT = 2f;
    }

    /** 法典·重塑已有 NPC：重绘构梦者外观并重建其纹理。 */
    private void applyGuideRepaint(PaintedLook look) {
        guide.setLook(look);
        if (guideTex != null) {
            guideTex.dispose();
        }
        guideTex = new Texture(lookPixmap(look.colors));
        guideTex.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        guideRegion = new TextureRegion(guideTex);
        logStory("以法典重塑了构梦者的样貌");
        toast = "构梦者样貌已重塑";
        toastT = 2f;
    }

    /* ---------------- 木箱（存储家具，内容随存档持久） ---------------- */

    private Inventory chestAt(int idx) {
        return chests.computeIfAbsent(idx, k -> {
            int w = world.getWidth();
            int cap = world.blockAt(k % w, k / w).storageCapacity();
            return new Inventory(Math.max(1, cap));
        });
    }

    private float[] chestPanel(int slots) {
        int rows = (slots + INV_COLS - 1) / INV_COLS;
        float slot = uiCam.viewportHeight * 0.05f, gap = slot * 0.14f;
        float gridW = INV_COLS * slot + (INV_COLS - 1) * gap;
        float gridH = rows * slot + (rows - 1) * gap;
        float pad = slot * 0.6f;
        float w = gridW + 2 * pad;
        float h = gridH + 2 * pad + slot * 1.6f;   // 多留一行放按钮
        float x = uiCam.viewportWidth / 2f - w / 2f, y = uiCam.viewportHeight / 2f - h / 2f;
        return new float[]{x, y, w, h, slot, gap, pad};
    }

    private float[] chestSlotRect(int i, float[] P) {
        float slot = P[4], gap = P[5], pad = P[6];
        float left = P[0] + pad, top = P[1] + P[3] - pad - P[4] * 1.6f;   // 顶部留标题、底部留按钮
        int r = i / INV_COLS, c = i % INV_COLS;
        return new float[]{left + c * (slot + gap), top - (r + 1) * slot - r * gap, slot};
    }

    private float[] chestDepositRect(float[] P) {
        float slot = P[4];
        return new float[]{P[0] + P[6], P[1] + P[6], slot * 4.5f, slot * 1.05f};
    }

    private void drawChest() {
        if (openChest < 0) {
            return;
        }
        Inventory ch = chestAt(openChest);
        int slots = ch.size();
        float[] P = chestPanel(slots);
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        WoodUi.panel(batch, pixel, P[0], P[1], P[2], P[3]);
        font.setColor(0.95f, 0.9f, 0.7f, 1f);
        font.draw(batch, "木箱", P[0] + P[6], P[1] + P[3] - 16);
        font.setColor(1, 1, 1, 1);
        for (int i = 0; i < slots; i++) {
            float[] r = chestSlotRect(i, P);
            drawSlot(r[0], r[1], r[2], ch.itemAt(i), ch.countAt(i), false);
        }
        float[] b = chestDepositRect(P);
        WoodUi.plank(batch, pixel, b[0], b[1], b[2], b[3], false);
        font.setColor(1, 1, 1, 1);
        font.draw(batch, "存入手持格", b[0] + 12, b[1] + b[3] / 2f + 6);
        font.setColor(0.7f, 0.72f, 0.85f, 1f);
        font.draw(batch, "点箱内格=取出到背包 · 右键/Esc 关闭", b[0] + b[2] + 16, b[1] + b[3] / 2f + 6);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    private void handleChestClick() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            openChest = -1;
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        Inventory ch = chestAt(openChest);
        float[] P = chestPanel(ch.size());
        float mx = uiMouseX(), my = uiMouseY();
        float[] b = chestDepositRect(P);
        if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {   // 存入
            Item it = inventory.selectedItem();
            if (it != null) {
                int c = inventory.countAt(inventory.selected());
                if (ch.add(it, c)) {
                    inventory.remove(it, c);
                }
            }
            return;
        }
        for (int i = 0; i < ch.size(); i++) {                                       // 取出
            float[] r = chestSlotRect(i, P);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[2]) {
                Item it = ch.itemAt(i);
                if (it != null) {
                    int c = ch.countAt(i);
                    if (inventory.add(it, c)) {
                        ch.remove(it, c);
                    }
                }
                return;
            }
        }
    }

    /** 商店内点击路由：买法典 / 关闭。 */
    private void handleShopClick() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)
                || Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            shopOpen = false;
            return;
        }
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float[] b = shopBuyRect();
            float mx = uiMouseX(), my = uiMouseY();
            if (mx >= b[0] && mx <= b[0] + b[2] && my >= b[1] && my <= b[1] + b[3]) {
                if (guide.canSellCodex(coins)) {
                    coins -= GuideNpc.CODEX_PRICE;
                    guide.sellCodex();
                    logStory("向构梦者购得《世界准则法典》");
                    toast = "购得《世界准则法典》！三类造梦画板已解锁";
                    toastT = 3f;
                } else if (!guide.codexOwned()) {
                    toast = "铸梦币不足（需 " + GuideNpc.CODEX_PRICE + "）";
                    toastT = 2f;
                }
            }
        }
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

    /* ---------------- 多格家具：摆放 / 锚点 / 破坏 ---------------- */

    /** 以 (ax,ay) 为左下角放置 b 的 footprint（需全部空格、不压玩家）；成功则扣手持。 */
    private void placeBlock(int ax, int ay, BlockType b) {
        int fw = b.footprintW(), fh = b.footprintH(), w = world.getWidth();
        for (int dx = 0; dx < fw; dx++) {
            for (int dy = 0; dy < fh; dy++) {
                int x = ax + dx, y = ay + dy;
                BlockType cur = world.blockAt(x, y);
                boolean free = cur == BlockType.AIR || cur.backgroundWall();   // 背景木墙属背景层→家具可占用其前景（替换该格墙）
                if (!world.inBounds(x, y) || !free || player.overlapsTile(x, y)) {
                    return;   // 放不下
                }
            }
        }
        for (int dx = 0; dx < fw; dx++) {
            for (int dy = 0; dy < fh; dy++) {
                int x = ax + dx, y = ay + dy;
                world.setBlock(x, y, b);
                edits.put(y * w + x, (int) b.id());
            }
        }
        inventory.takeSelectedOne();
        lightDirty = true;
    }

    /** 把墙面对象（火把）挂到 (x,y) 的背景墙/实心墙上；成功返回 true（消耗 1 个）。 */
    private boolean tryMount(int x, int y, BlockType b) {
        if (!b.wallMountable()) {
            return false;
        }
        BlockType behind = world.blockAt(x, y);
        if (!(behind.backgroundWall() || behind.solid())) {
            return false;   // 需有墙可挂
        }
        if (world.mountAt(x, y) != BlockType.AIR) {
            return false;   // 已挂对象
        }
        world.setMount(x, y, b);
        mountEdits.put(y * world.getWidth() + x, (int) b.id());
        inventory.takeSelectedOne();
        lightDirty = true;
        return true;
    }

    /** 摘下墙面对象（掉回物品、清第二层）。 */
    private void popMount(int x, int y) {
        BlockType m = world.mountAt(x, y);
        if (m == BlockType.AIR) {
            return;
        }
        world.setMount(x, y, BlockType.AIR);
        mountEdits.put(y * world.getWidth() + x, (int) BlockType.AIR.id());
        spawnDrop(x, y, Item.ofBlock(m));
        lightDirty = true;
    }

    /** 从 (x,y) 反推 b 的左下角锚点（扫描 footprint 候选，取四角全为 b 者）。 */
    private int[] anchorOf(int x, int y, BlockType b) {
        int fw = b.footprintW(), fh = b.footprintH();
        for (int ay = y; ay > y - fh; ay--) {
            for (int ax = x; ax > x - fw; ax--) {
                boolean ok = true;
                for (int dx = 0; dx < fw && ok; dx++) {
                    for (int dy = 0; dy < fh && ok; dy++) {
                        if (world.blockAt(ax + dx, ay + dy) != b) {
                            ok = false;
                        }
                    }
                }
                if (ok) {
                    return new int[]{ax, ay};
                }
            }
        }
        return new int[]{x, y};
    }

    /** 破坏 (x,y) 处方块：多格家具整体清除、整体掉 1。 */
    private void breakBlock(int x, int y, BlockType b) {
        int w = world.getWidth();
        int[] a = anchorOf(x, y, b);
        int fw = b.footprintW(), fh = b.footprintH();
        for (int dx = 0; dx < fw; dx++) {
            for (int dy = 0; dy < fh; dy++) {
                int cx = a[0] + dx, cy = a[1] + dy;
                if (world.inBounds(cx, cy) && world.blockAt(cx, cy) == b) {
                    world.setBlock(cx, cy, BlockType.AIR);
                    edits.put(cy * w + cx, (int) BlockType.AIR.id());
                    if (world.mountAt(cx, cy) != BlockType.AIR) {   // 连带摘除墙上对象
                        world.setMount(cx, cy, BlockType.AIR);
                        mountEdits.put(cy * w + cx, (int) BlockType.AIR.id());
                    }
                }
            }
        }
        spawnDrop(x, y, Item.ofBlock(b));
        if (b.isSmelter()) {                       // 熔炉被拆→掉出内部矿石/金属锭
            int anchor = a[1] * w + a[0];
            FurnaceData f = furnaces.remove(anchor);
            if (f != null) {
                for (int i = 0; i < FURN_SLOTS; i++) {
                    if (f.inId[i] >= 0 && f.inN[i] > 0) {
                        spawnDropN(x, y, Item.byId(f.inId[i]), f.inN[i]);
                    }
                    if (f.outId[i] >= 0 && f.outN[i] > 0) {
                        spawnDropN(x, y, Item.byId(f.outId[i]), f.outN[i]);
                    }
                }
            }
            if (openFurnace == anchor) {
                openFurnace = -1;
            }
        }
        lightDirty = true;
    }

    /* ---------------- 树对象：种植 / 生长 / 砍伐 / 渲染 ---------------- */

    /** 树底部中间格（贴地、两侧为根）= 唯一可砍点。 */
    private boolean isTreeChopCell(int x, int y) {
        return world.blockAt(x, y) == BlockType.TREE
                && world.blockAt(x, y - 1) != BlockType.TREE
                && world.blockAt(x - 1, y) == BlockType.TREE
                && world.blockAt(x + 1, y) == BlockType.TREE;
    }

    /** (ax, y) 这一行三列是否都是树（ax 为底行最左列）。 */
    private boolean rowAllTree(int ax, int y) {
        for (int c = 0; c < BlockType.TREE_W; c++) {
            if (world.blockAt(ax + c, y) != BlockType.TREE) {
                return false;
            }
        }
        return true;
    }

    /** 扫描世界重建树表（读档/新档后调用；每棵按“底行最左格”识别一次）。 */
    private void rebuildTreeRegistry() {
        trees.clear();
        int W = world.getWidth(), H = world.getHeight();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (world.blockAt(x, y) != BlockType.TREE) {
                    continue;
                }
                if (world.blockAt(x, y - 1) == BlockType.TREE || world.blockAt(x - 1, y) == BlockType.TREE) {
                    continue;   // 非“底行最左”→跳过
                }
                int h = 0;
                while (rowAllTree(x, y + h)) {
                    h++;
                }
                if (h > 0) {
                    trees.put(y * W + x, new TreeData(x, y, h));
                }
            }
        }
        growing.keySet().retainAll(trees.keySet());   // 锚点已消失（被砍）→清生长态
    }

    /** 以 (cx,cy) 为树干底部中央种一棵树苗（起始高 TREE_START_H，随机目标高，随后逐格长高）。 */
    private void plantTree(int cx, int cy) {
        int ax = cx - 1, ay = cy;
        int h0 = BlockType.TREE_START_H;
        for (int c = 0; c < BlockType.TREE_W; c++) {
            if (!world.blockAt(ax + c, ay - 1).solid()) {
                return;   // 底行下方须是实心地面
            }
        }
        for (int dy = 0; dy < h0; dy++) {
            for (int c = 0; c < BlockType.TREE_W; c++) {
                int x = ax + c, y = ay + dy;
                if (!world.inBounds(x, y) || world.blockAt(x, y) != BlockType.AIR || player.overlapsTile(x, y)) {
                    return;   // 空间不足/压玩家→不放
                }
            }
        }
        for (int dy = 0; dy < h0; dy++) {
            for (int c = 0; c < BlockType.TREE_W; c++) {
                setTileEdit(ax + c, ay + dy, BlockType.TREE);
            }
        }
        int anchor = ay * world.getWidth() + ax;
        trees.put(anchor, new TreeData(ax, ay, h0));
        int targetH = BlockType.TREE_MIN_H
                + (int) (Math.random() * (BlockType.TREE_MAX_H - BlockType.TREE_MIN_H + 1));
        growing.put(anchor, new GrowState(0f, targetH));
        inventory.takeSelectedOne();
        toast = "种下一棵树苗（会慢慢长高）";
        toastT = 1.6f;
    }

    /** 每帧推进未长成树：到点则向上长 1 格（顶行需全 AIR），达目标高转成熟。 */
    private void updateTrees(float dt) {
        if (growing.isEmpty()) {
            return;
        }
        java.util.Iterator<java.util.Map.Entry<Integer, GrowState>> it = growing.entrySet().iterator();
        while (it.hasNext()) {
            java.util.Map.Entry<Integer, GrowState> en = it.next();
            TreeData td = trees.get(en.getKey());
            if (td == null) {
                it.remove();
                continue;
            }
            GrowState g = en.getValue();
            g.age += dt;
            while (g.age >= TREE_GROW_SECONDS && td.h < g.targetH) {
                g.age -= TREE_GROW_SECONDS;
                int topY = td.ay + td.h;
                boolean space = true;
                for (int c = 0; c < BlockType.TREE_W; c++) {
                    if (world.blockAt(td.ax + c, topY) != BlockType.AIR) {
                        space = false;
                    }
                }
                if (!space) {
                    g.age = TREE_GROW_SECONDS;   // 顶被堵→等待空间腾出
                    break;
                }
                for (int c = 0; c < BlockType.TREE_W; c++) {
                    setTileEdit(td.ax + c, topY, BlockType.TREE);
                }
                td.h++;
            }
            if (td.h >= g.targetH) {
                it.remove();   // 成熟
            }
        }
    }

    /** 砍倒整棵：从底部中央格量出矩形、整体清除、按高度掉木材 + 树苗（可再生）。 */
    private void chopTree(int cx, int by) {
        int ax = cx - 1;
        int h = 0;
        while (rowAllTree(ax, by + h)) {
            h++;
        }
        if (h <= 0) {
            return;
        }
        for (int dy = 0; dy < h; dy++) {
            for (int c = 0; c < BlockType.TREE_W; c++) {
                setTileEdit(ax + c, by + dy, BlockType.AIR);
            }
        }
        int anchor = by * world.getWidth() + ax;
        trees.remove(anchor);
        growing.remove(anchor);
        int wood = woodByHeight(h);
        spawnDropN(cx, by, Item.ofBlock(BlockType.WOOD), wood);
        spawnDropN(cx, by, Item.ofBlock(BlockType.TREE), 1 + (int) (Math.random() * 2));
        toast = "砍倒一棵树：木材 +" + wood;
        toastT = 1.6f;
    }

    /** 木材数∝树高（6~16 高 → 约 6~16 木材 + 少量随机）。 */
    private int woodByHeight(int h) {
        int w = (int) Math.round(h * 0.9) + (int) (Math.random() * 3);
        if (w < 6) {
            w = 6;
        }
        if (w > 16) {
            w = 16;
        }
        return w;
    }

    /** 改方块并记入 diff（树占用/生长/砍伐统一走这里）。 */
    private void setTileEdit(int x, int y, BlockType b) {
        if (!world.inBounds(x, y)) {
            return;
        }
        world.setBlock(x, y, b);
        edits.put(y * world.getWidth() + x, (int) b.id());
    }

    /** 掉落指定数量的物品（默认 spawnDrop 只掉 1）。 */
    private void spawnDropN(int tileX, int tileY, Item it, int count) {
        if (it == null || count <= 0) {
            return;
        }
        drops.add(new ItemDrop(tileX * (float) TILE + (TILE - ItemDrop.SIZE) / 2f,
                tileY * (float) TILE + 2f, it, count));
    }

    /** 视口内逐棵画整树贴图（按 3×h 矩形纵向拉伸）。 */
    private void drawTrees() {
        float halfW = camera.viewportWidth / 2, halfH = camera.viewportHeight / 2;
        float vx0 = camera.position.x - halfW, vx1 = camera.position.x + halfW;
        float vy0 = camera.position.y - halfH, vy1 = camera.position.y + halfH;
        batch.setColor(1, 1, 1, 1);
        for (TreeData t : trees.values()) {
            float px = t.ax * (float) TILE, py = t.ay * (float) TILE;
            float pw = BlockType.TREE_W * (float) TILE, ph = t.h * (float) TILE;
            if (px > vx1 || px + pw < vx0 || py > vy1 || py + ph < vy0) {
                continue;
            }
            batch.draw(treeRegion, px, py, pw, ph);
        }
    }

    /** 树数据（锚点=底行最左格 + 当前高）。 */
    private static final class TreeData {
        int ax;
        int ay;
        int h;
        TreeData(int ax, int ay, int h) {
            this.ax = ax;
            this.ay = ay;
            this.h = h;
        }
    }

    /** 生长态（仅未长成的玩家树）。 */
    private static final class GrowState {
        float age;
        int targetH;
        GrowState(float age, int targetH) {
            this.age = age;
            this.targetH = targetH;
        }
    }

    /* ---------------- 熔炉：熔铸界面 + 存取 + 即时熔炼 ---------------- */

    /** 取/建某锚点的熔炉数据（4 输入 + 4 输出）。 */
    private FurnaceData furnaceAt(int anchor) {
        return furnaces.computeIfAbsent(anchor, k -> new FurnaceData());
    }

    /** 即时熔炼：每列输入格够 3 个同类矿石→扣 3 产 1 对应锭进下方格（产物满/异种则停）。 */
    private void smeltTick(FurnaceData f) {
        for (int i = 0; i < FURN_SLOTS; i++) {
            if (f.inId[i] < 0 || f.inN[i] < 3) {
                continue;
            }
            Item res = Item.smeltResult(Item.byId(f.inId[i]));
            if (res == null) {
                continue;
            }
            if (f.outId[i] < 0) {
                f.outId[i] = res.id();
            } else if (f.outId[i] != res.id()) {
                continue;   // 产物格是别的锭→先不熔
            }
            int make = Math.min(f.inN[i] / 3, 999 - f.outN[i]);
            if (make > 0) {
                f.outN[i] += make;
                f.inN[i] -= make * 3;
            }
            if (f.inN[i] <= 0) {
                f.inId[i] = -1;
                f.inN[i] = 0;
            }
        }
    }

    private float[] furnaceLayout() {
        float slot = uiCam.viewportHeight * 0.052f, gap = slot * 0.18f, pad = slot * 0.7f;
        float titleH = slot * 1.2f, arrowH = slot * 0.9f, hintH = slot * 0.9f;
        float gridW = FURN_SLOTS * slot + (FURN_SLOTS - 1) * gap;
        float w = gridW + 2 * pad;
        float h = pad + titleH + slot + arrowH + slot + hintH + pad;
        float x = uiCam.viewportWidth / 2f - w / 2f, y = uiCam.viewportHeight / 2f - h / 2f;
        return new float[]{x, y, w, h, slot, gap, pad, titleH, arrowH, hintH};
    }

    /** col=列(0..3)，row=0 上排输入 / 1 下排输出。返回 {x,y,size}。 */
    private float[] furnaceSlotRect(int col, int row, float[] P) {
        float y0 = P[1], h = P[3], slot = P[4], gap = P[5], pad = P[6], titleH = P[7], arrowH = P[8];
        float left = P[0] + pad;
        float inputY = y0 + h - pad - titleH - slot;         // 上排输入
        float outY = inputY - arrowH - slot;                 // 下排输出
        float x = left + col * (slot + gap);
        return new float[]{x, row == 0 ? inputY : outY, slot};
    }

    private void drawFurnace() {
        if (openFurnace < 0) {
            return;
        }
        FurnaceData f = furnaceAt(openFurnace);
        float[] P = furnaceLayout();
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        WoodUi.panel(batch, pixel, P[0], P[1], P[2], P[3]);
        font.setColor(0.95f, 0.9f, 0.7f, 1f);
        font.draw(batch, "熔炉 · 熔铸", P[0] + P[6], P[1] + P[3] - P[6] - P[7] * 0.5f);
        font.setColor(1, 1, 1, 1);
        float mx = uiMouseX(), my = uiMouseY();
        for (int col = 0; col < FURN_SLOTS; col++) {
            float[] ri = furnaceSlotRect(col, 0, P);
            float[] ro = furnaceSlotRect(col, 1, P);
            boolean hovI = mx >= ri[0] && mx <= ri[0] + ri[2] && my >= ri[1] && my <= ri[1] + ri[2];
            boolean hovO = mx >= ro[0] && mx <= ro[0] + ro[2] && my >= ro[1] && my <= ro[1] + ro[2];
            drawSlot(ri[0], ri[1], ri[2], f.inId[col] < 0 ? null : Item.byId(f.inId[col]), f.inN[col], hovI);
            drawSlot(ro[0], ro[1], ro[2], f.outId[col] < 0 ? null : Item.byId(f.outId[col]), f.outN[col], hovO);
            float cx = ri[0] + ri[2] / 2f;                 // 向下箭头（输入→产物）
            float midY = (ri[1] + (ro[1] + ro[2])) / 2f;
            batch.setColor(1f, 0.85f, 0.3f, 0.95f);
            batch.draw(pixel, cx - 1.5f, midY - 8f, 3f, 16f);
            batch.draw(pixel, cx - 6f, midY - 10f, 12f, 3f);
            batch.setColor(1, 1, 1, 1);
        }
        font.setColor(0.7f, 0.72f, 0.85f, 1f);
        font.draw(batch, "上排放矿石（每 3 个熔成 1 锭）· 点下排取金属锭 · 右键/Esc 关闭",
                P[0] + P[6], P[1] + P[6] + P[9] * 0.4f);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    private void handleFurnaceClick() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT)) {
            openFurnace = -1;
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        FurnaceData f = furnaceAt(openFurnace);
        float[] P = furnaceLayout();
        float mx = uiMouseX(), my = uiMouseY();
        Item held = inventory.selectedItem();
        for (int col = 0; col < FURN_SLOTS; col++) {
            float[] ri = furnaceSlotRect(col, 0, P);
            if (mx >= ri[0] && mx <= ri[0] + ri[2] && my >= ri[1] && my <= ri[1] + ri[2]) {
                depositOre(f, col, held);
                smeltTick(f);
                return;
            }
            float[] ro = furnaceSlotRect(col, 1, P);
            if (mx >= ro[0] && mx <= ro[0] + ro[2] && my >= ro[1] && my <= ro[1] + ro[2]) {
                takeIngot(f, col);
                return;
            }
        }
    }

    /** 上排：手持矿石存入该列输入格（同类可叠、上限 999）；无可熔手持→取回矿石。 */
    private void depositOre(FurnaceData f, int col, Item held) {
        if (held != null && Item.smeltable(held)) {
            if (f.inId[col] < 0) {
                int move = Math.min(inventory.countAt(inventory.selected()), 999);
                f.inId[col] = held.id();
                f.inN[col] = move;
                inventory.remove(held, move);
            } else if (f.inId[col] == held.id()) {
                int move = Math.min(inventory.countAt(inventory.selected()), Math.max(0, 999 - f.inN[col]));
                if (move > 0) {
                    f.inN[col] += move;
                    inventory.remove(held, move);
                }
            } else {
                toast = "该格已是别的矿石";
                toastT = 1.4f;
            }
            return;
        }
        if (f.inId[col] >= 0 && f.inN[col] > 0) {   // 无可熔手持→取回输入矿石
            Item it = Item.byId(f.inId[col]);
            int c = f.inN[col];
            if (inventory.add(it, c)) {
                f.inId[col] = -1;
                f.inN[col] = 0;
            } else {
                toast = "背包已满";
                toastT = 1.4f;
            }
        }
    }

    /** 下排：取回该列熔好的金属锭。 */
    private void takeIngot(FurnaceData f, int col) {
        if (f.outId[col] >= 0 && f.outN[col] > 0) {
            Item it = Item.byId(f.outId[col]);
            int c = f.outN[col];
            if (inventory.add(it, c)) {
                f.outId[col] = -1;
                f.outN[col] = 0;
            } else {
                toast = "背包已满";
                toastT = 1.4f;
            }
        }
    }

    /** 熔炉一格数据：4 输入（矿石）+ 4 输出（金属锭），按列配对。 */
    private static final class FurnaceData {
        final int[] inId;
        final int[] inN;
        final int[] outId;
        final int[] outN;
        FurnaceData() {
            inId = new int[FURN_SLOTS];
            java.util.Arrays.fill(inId, -1);
            inN = new int[FURN_SLOTS];
            outId = new int[FURN_SLOTS];
            java.util.Arrays.fill(outId, -1);
            outN = new int[FURN_SLOTS];
        }

        boolean hasContent() {
            for (int i = 0; i < FURN_SLOTS; i++) {
                if (inN[i] > 0 || outN[i] > 0) {
                    return true;
                }
            }
            return false;
        }
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
                if (b == BlockType.AIR || b.isTree()) {
                    continue;   // 树整体由 drawTrees 单独绘制
                }
                float px = x * (float) TILE, py = y * (float) TILE;
                batch.setColor(1, 1, 1, 1);
                batch.draw(tileRegion(b, x, y), px, py, TILE, TILE);
                BlockType mo = world.mountAt(x, y);
                if (mo != BlockType.AIR) {
                    batch.draw(tileRegion(mo, x, y), px, py, TILE, TILE);   // 墙面对象叠在墙上
                }
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

    /** 连续亮度 0~1（double/float）：天光（非背景墙连通透光的强度，已由 LightEngine 按昼强缩放前的几何值）与块光、动态光取最大。 */
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

    /** 计算角色视觉参数（bob/tilt/facing/尺寸）+ 程序化姿态，交给蒙皮渲染。 */
    private void updatePlayerVisual() {
        pvW = player.width();
        pvH = player.height();
        pvFacing = player.facing();
        boolean walking = player.isMoving() && player.isOnGround();
        boolean swimming = player.isInWater();
        if (swimming) {
            pvBob = (float) Math.sin(time * 4f) * 1.8f;
            pvTilt = pvFacing > 0 ? 80f : -80f;
        } else if (walking) {
            pvBob = (float) Math.abs(Math.sin(walkPhase)) * 2.2f;
            pvTilt = (float) Math.sin(walkPhase) * 3f;
        } else if (!player.isOnGround()) {
            pvBob = 0f;
            pvTilt = pvFacing > 0 ? 6f : -6f;
        } else {
            pvBob = 0f;
            pvTilt = 0f;
        }
        if (walking) {
            avatarPhase += frameDelta * 9f;
        } else {
            avatarPhase = 0f;
        }
        buildAvatarPose(walking);
        if (avatar != null) {
            avatar.applyPose(avatarPose);
        }
    }

    /** 程序化姿态：待机呼吸 + 走路四肢摆动（前臂/双腿反相）。一次性动作后续由 Animator/overlay 接管。 */
    private void buildAvatarPose(boolean walking) {
        avatarPose.reset();
        float breath = (float) Math.sin(time * 2f);
        avatarPose.setBone(BoneId.SPINE, breath * 1.2f, 0, 0, 1, 1);
        avatarPose.setBone(BoneId.HEAD, -breath * 1.6f, 0, 0, 1, 1);
        if (walking) {
            float sw = (float) Math.sin(avatarPhase);
            float amp = 26f;
            avatarPose.setBone(BoneId.THIGH_F, sw * amp, 0, 0, 1, 1);
            avatarPose.setBone(BoneId.THIGH_B, -sw * amp, 0, 0, 1, 1);
            avatarPose.setBone(BoneId.SHIN_F, Math.max(0f, -sw) * amp * 0.7f, 0, 0, 1, 1);
            avatarPose.setBone(BoneId.SHIN_B, Math.max(0f, sw) * amp * 0.7f, 0, 0, 1, 1);
            avatarPose.setBone(BoneId.ARM_FB, -sw * amp * 0.6f, 0, 0, 1, 1);
            avatarPose.setBone(BoneId.ARM_UB, sw * amp * 0.6f, 0, 0, 1, 1);
        } else {
            avatarPose.setBone(BoneId.ARM_FB, -4f + breath * 2f, 0, 0, 1, 1);
        }
    }

    private void drawPlayer() {
        float w = player.width();
        float h = player.height();
        int facing = player.facing();
        boolean walking = player.isMoving() && player.isOnGround();
        boolean swimming = player.isInWater();
        // 走路：上下颠 + 左右轻摆；空中：前倾；游泳：身体横卧 + 随波浮动
        float bob;
        float tilt;
        if (swimming) {
            bob = (float) Math.sin(time * 4f) * 1.8f;
            tilt = facing > 0 ? 80f : -80f;
        } else {
            bob = walking ? (float) Math.abs(Math.sin(walkPhase)) * 2.2f : 0f;
            if (walking) {
                tilt = (float) Math.sin(walkPhase) * 3f;
            } else if (!player.isOnGround()) {
                tilt = facing > 0 ? 6f : -6f;
            } else {
                tilt = 0f;
            }
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
                d.glideTo(pcx, pcy, MAGNET_SPEED, dt);   // 磁吸：穿墙飞向玩家
            } else {
                d.update(world, dt);                      // 未磁吸：正常重力/碰撞
            }
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

    /** 仆从推进（跟随/索敌/攻击，到期或死亡移除）；索敌范围=当前视框内所有怪。 */
    private void updateServants(float dt) {
        float pcx = player.centerX(), pcy = player.centerY();
        float halfW = camera.viewportWidth / 2f, halfH = camera.viewportHeight / 2f;
        float vcx = camera.position.x, vcy = camera.position.y;
        for (java.util.Iterator<Servant> it = servants.iterator(); it.hasNext(); ) {
            if (!it.next().update(pcx, pcy, spawner.mobs(), dt, vcx, vcy, halfW, halfH)) {
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
        float viewHalf = camera.viewportWidth / TILE / 2f;
        // 同屏“居民”数（当前以在场构梦者代理；入住系统接入后计真实住客）：≥3=小镇→不刷，≥1=安全区→×0.2
        int residents = (guide.present()
                && Math.abs(guide.x() - player.centerX()) / TILE <= viewHalf
                && Math.abs(guide.y() - player.centerY()) / TILE <= viewHalf) ? 1 : 0;
        spawner.update(world, player.centerX(), player.centerY(), clock.isNight(), dt, viewHalf, residents);
        for (Mob m : spawner.drainKilled()) {          // 任何来源击杀→集中掉 2~4 枚铸梦币
            spawnCoinDrop(m);
        }
        for (Mob m : spawner.mobs()) {                       // 接触伤害：stats.hurt 自带无敌帧（防灌伤）
            if (m.overlapsRect(player.x(), player.y(), player.width(), player.height())) {
                stats.hurt(m instanceof Slime ? ((Slime) m).touchDamage()
                        : m instanceof Bat ? ((Bat) m).touchDamage() : 5);
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
            if (m instanceof Bat) {
                drawBat((Bat) m);
                continue;
            }
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
    
    /** 蝙蝠：暗色身体 + 上下扇动的翅膀 + 小亮眼 + 受伤血条。 */
    private void drawBat(Bat b) {
        float x = b.x(), y = b.y(), w = b.w(), h = b.h();
        float cx = x + w / 2f, cy = y + h / 2f;
        float flap = (float) Math.sin(b.flap() * 16f);          // -1..1 扇翅
        if (b.isInvulnerable()) {
            batch.setColor(0.75f, 0.65f, 0.75f, 1f);            // 无敌帧闪白
        } else {
            batch.setColor(0.16f, 0.13f, 0.2f, 1f);             // 暗紫黑身体
        }
        batch.draw(pixel, x + w * 0.3f, y, w * 0.4f, h);        // 身体
        float wy = cy + flap * 4f;
        batch.draw(pixel, x - 6, wy - 2, w * 0.4f + 6, 4);      // 左翅
        batch.draw(pixel, x + w * 0.6f, wy - 2, w * 0.4f + 6, 4); // 右翅
        batch.setColor(1f, 0.85f, 0.3f, 1f);                    // 眼
        batch.draw(pixel, cx - 3, cy + 1, 2, 2);
        batch.draw(pixel, cx + 1, cy + 1, 2, 2);
        batch.setColor(1, 1, 1, 1);
        if (b.hp() < b.maxHp()) {
            batch.setColor(0.1f, 0.1f, 0.1f, 0.8f);
            batch.draw(pixel, x, y + h + 3, w, 3);
            batch.setColor(0.85f, 0.25f, 0.25f, 1f);
            batch.draw(pixel, x, y + h + 3, w * (b.hp() / (float) b.maxHp()), 3);
            batch.setColor(1, 1, 1, 1);
        }
    }
    
    /** 拾取动画：物品图标从掉落点飞向玩家中心。 */
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

    /** 物品图标：优先用玩家重绘外观；否则可放置方块用其贴图，否则按 id 取镐/斧/职业武器图。 */
    private TextureRegion itemSprite(Item it) {
        Texture pt = itemPaintTex.get(it.id());
        if (pt != null) {
            return new TextureRegion(pt);
        }
        if (it.kind() == Item.Kind.COIN) {
            return coinRegion;
        }
        if (it.placeable()) {
            if (it.block().isTree()) {
                return treeRegion;   // 树物品用整树剪影作图标
            }
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

    /** 合成页：面板内网格排布 + 右侧滞动条（滚轮翻页），悬停弹详情。 */
    private java.util.List<Recipe> unlockedRecipes() {
        java.util.List<Recipe> un = new java.util.ArrayList<>();
        for (Recipe r : recipes) {
            if (r.unlocked) {
                un.add(r);
            }
        }
        return un;
    }

    /** 可见格数（行×列）；列=背包列数、行按内容高。 */
    private int craftVisibleCount(float[] L) {
        float slot = L[0], gap = L[1], contentH = L[4];
        int rows = Math.max(1, (int) ((contentH + gap) / (slot + gap)));
        return rows * INV_COLS;
    }

    private void drawCraftTab(float[] L) {
        java.util.List<Recipe> un = unlockedRecipes();
        int total = un.size();
        int visCount = craftVisibleCount(L);
        int maxScroll = Math.max(0, total - visCount);
        if (craftScroll > maxScroll) {
            craftScroll = maxScroll;
        }
        if (craftScroll < 0) {
            craftScroll = 0;
        }
        float mx = uiMouseX(), my = uiMouseY();
        Recipe hover = null;
        for (int v = 0; v < visCount; v++) {
            int idx = craftScroll + v;
            if (idx >= total) {
                break;
            }
            Recipe rec = un.get(idx);
            float[] r = bagSlotRect(v, L);   // 复用背包网格坐标
            boolean ok = rec.canCraft(inventory);
            boolean over = mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[2];
            WoodUi.plank(batch, pixel, r[0], r[1], r[2], r[2], ok || over);
            batch.setColor(1, 1, 1, ok ? 1f : 0.4f);
            batch.draw(itemSprite(rec.out), r[0] + r[2] * 0.1f, r[1] + r[2] * 0.1f, r[2] * 0.8f, r[2] * 0.8f);
            batch.setColor(1, 1, 1, 1);
            if (over) {
                hover = rec;
            }
        }
        if (total > visCount) {
            drawCraftScrollbar(L, total, visCount, maxScroll);
        }
        if (hover != null) {
            drawCraftTooltip(hover, mx, my);
        }
    }

    /** 合成页滞动条（内容区右侧）：槽 + 把手。 */
    private void drawCraftScrollbar(float[] L, int total, int visCount, int maxScroll) {
        float slot = L[0], gap = L[1], contentH = L[4], gridW = L[3], cx0 = L[9], cy0 = L[10];
        float trackX = cx0 + gridW + gap * 0.3f, trackW = slot * 0.22f;
        fill(trackX, cy0 - contentH, trackW, contentH, 0f, 0f, 0f, 0.35f);
        float frac = (float) visCount / total;
        float thumbH = Math.max(contentH * 0.15f, contentH * frac);
        float scrollFrac = maxScroll > 0 ? (float) craftScroll / maxScroll : 0f;
        float thumbY = cy0 - thumbH - (contentH - thumbH) * scrollFrac;
        fill(trackX, thumbY, trackW, thumbH, 0.85f, 0.75f, 0.5f, 1f);
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

    /** 鼠标是否在合成页内容网格区内（滚轮翻页的作区）。 */
    private boolean craftAreaHover() {
        float[] L = invLayout();
        float mx = uiMouseX(), my = uiMouseY();
        float cx0 = L[9], cy0 = L[10], gridW = L[3], contentH = L[4];
        return mx >= cx0 && mx <= cx0 + gridW && my <= cy0 && my >= cy0 - contentH;
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
            java.util.List<Recipe> un = unlockedRecipes();
            int visCount = craftVisibleCount(L);
            for (int v = 0; v < visCount; v++) {
                int idx = craftScroll + v;
                if (idx >= un.size()) {
                    break;
                }
                float[] r = bagSlotRect(v, L);
                if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[2]) {
                    Recipe rec = un.get(idx);
                    if (rec.craft(inventory)) {
                        toast = "合成：" + rec.out.cn();
                        toastT = 1.5f;
                    }
                    return;
                }
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
        // 物品名：悬停某格优先显示该格，否则显示当前选中格
        Item shown = inventory.selectedItem();
        float hmx = uiMouseX(), hmy = uiMouseY();
        for (int i = 0; i < n; i++) {
            float x = x0 + i * (slot + gap);
            if (hmx >= x && hmx <= x + slot && hmy >= y0 && hmy <= y0 + slot) {
                shown = inventory.itemAt(i);
                break;
            }
        }
        if (shown != null) {
            font.setColor(1f, 0.92f, 0.55f, 1f);
            font.draw(batch, shown.cn(), x0, y0 + slot + 42);
            font.setColor(1, 1, 1, 1);
        }
        batch.end();
    }

    private void drawCursor() {
        int[] tile = mouseTile();
        if (tile == null) {
            return;
        }
        BlockType b = world.blockAt(tile[0], tile[1]);
        if (b.storageCapacity() > 0 || b.isSmelter() || b == BlockType.WORKBENCH || b == BlockType.DOOR) {
            int[] a = anchorOf(tile[0], tile[1], b);   // 可交互方块→整片 footprint 金描边
            goldOutline(a[0], a[1], b.footprintW(), b.footprintH());
        } else if (guide.present() && pointerOverGuide()) {
            goldOutlinePx(guide.x(), guide.y(), TILE * 1.3f, TILE * 2.6f);   // 可对话 NPC→金描边
        } else {
            batch.setColor(1f, 1f, 0.4f, 0.22f);
            batch.draw(pixel, tile[0] * (float) TILE, tile[1] * (float) TILE, TILE, TILE);
            batch.setColor(1, 1, 1, 1);
        }
    }

    /** 世界格坐标系的金色描边（footprint 大小）：标记可交互内容被悬停。 */
    private void goldOutline(int ax, int ay, int fw, int fh) {
        goldOutlinePx(ax * (float) TILE, ay * (float) TILE, fw * (float) TILE, fh * (float) TILE);
    }

    private void goldOutlinePx(float x, float y, float w, float h) {
        float t = 2f;
        batch.setColor(1f, 0.85f, 0.3f, 0.95f);
        batch.draw(pixel, x, y + h - t, w, t);
        batch.draw(pixel, x, y, w, t);
        batch.draw(pixel, x, y, t, h);
        batch.draw(pixel, x + w - t, y, t, h);
        batch.setColor(1, 1, 1, 1);
    }

    /** 鼠标世界坐标是否落在构梦者贴图矩形内。 */
    private boolean pointerOverGuide() {
        mouseWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorld);
        float gx = guide.x(), gy = guide.y();
        return mouseWorld.x >= gx && mouseWorld.x <= gx + TILE * 1.3f
                && mouseWorld.y >= gy && mouseWorld.y <= gy + TILE * 2.6f;
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

    /** 设置存档显示名（新游戏创建时命名）。 */
    public void setSaveName(String name) {
        this.saveName = name == null ? "" : name;
    }

    /** 导出当前世界为存档数据（改动 diff + 玩家状态）。 */
    public GameSave toSave() {
        GameSave s = new GameSave();
        s.slot = slot;
        s.saveName = saveName;
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
        s.codexOwned = guide.codexOwned();
        s.guidePresent = guide.present();
        s.faceTemplateId = appearance.templateId;
        s.faceColors = appearance.colors.clone();
        s.storyLog = storyLog.toArray(new String[0]);
        int cn = chests.size();
        int[] cTile = new int[cn];
        int[] cCap = new int[cn];
        int total = 0;
        for (Inventory inv : chests.values()) {
            total += inv.size();
        }
        int[] cItem = new int[total];
        int[] cCount = new int[total];
        int ci = 0, off = 0;
        for (java.util.Map.Entry<Integer, Inventory> e : chests.entrySet()) {
            int sz = e.getValue().size();
            cTile[ci] = e.getKey();
            cCap[ci] = sz;
            System.arraycopy(e.getValue().itemIdSnapshot(), 0, cItem, off, sz);
            System.arraycopy(e.getValue().countSnapshot(), 0, cCount, off, sz);
            off += sz;
            ci++;
        }
        s.chestTile = cTile;
        s.chestCap = cCap;
        s.chestItem = cItem;
        s.chestCount = cCount;
        int pn = itemPaints.size();
        int[] pId = new int[pn];
        int[] pW = new int[pn];
        int[] pH = new int[pn];
        int ptotal = 0;
        for (PaintedLook pl : itemPaints.values()) {
            ptotal += pl.colors.length;
        }
        int[] pC = new int[ptotal];
        int pk = 0, poff = 0;
        for (java.util.Map.Entry<Integer, PaintedLook> e : itemPaints.entrySet()) {
            PaintedLook pl = e.getValue();
            pId[pk] = e.getKey();
            pW[pk] = pl.w;
            pH[pk] = pl.h;
            System.arraycopy(pl.colors, 0, pC, poff, pl.colors.length);
            poff += pl.colors.length;
            pk++;
        }
        s.paintItemId = pId;
        s.paintW = pW;
        s.paintH = pH;
        s.paintColors = pC;
        int mn = 0;
        for (int v : mountEdits.values()) {
            if (v != 0) {
                mn++;
            }
        }
        int[] mIdx = new int[mn];
        int[] mBlk = new int[mn];
        int mk = 0;
        for (java.util.Map.Entry<Integer, Integer> e : mountEdits.entrySet()) {
            if (e.getValue() != 0) {
                mIdx[mk] = e.getKey();
                mBlk[mk] = e.getValue();
                mk++;
            }
        }
        s.mountIdx = mIdx;
        s.mountBlock = mBlk;
        int gn = growing.size();
        int[] gAnchor = new int[gn];
        int[] gTarget = new int[gn];
        float[] gAge = new float[gn];
        int gi = 0;
        for (java.util.Map.Entry<Integer, GrowState> e : growing.entrySet()) {
            if (!trees.containsKey(e.getKey())) {
                continue;   // 双保险：只存仍在场的树
            }
            gAnchor[gi] = e.getKey();
            gTarget[gi] = e.getValue().targetH;
            gAge[gi] = e.getValue().age;
            gi++;
        }
        if (gi < gn) {
            gAnchor = java.util.Arrays.copyOf(gAnchor, gi);
            gTarget = java.util.Arrays.copyOf(gTarget, gi);
            gAge = java.util.Arrays.copyOf(gAge, gi);
        }
        s.growAnchor = gAnchor;
        s.growTargetH = gTarget;
        s.growAge = gAge;
        int fn = 0;
        for (FurnaceData f : furnaces.values()) {
            if (f.hasContent()) {
                fn++;
            }
        }
        int[] fAnc = new int[fn];
        int[] fiI = new int[fn * FURN_SLOTS];
        int[] fiN = new int[fn * FURN_SLOTS];
        int[] foI = new int[fn * FURN_SLOTS];
        int[] foN = new int[fn * FURN_SLOTS];
        int fk = 0;
        for (java.util.Map.Entry<Integer, FurnaceData> e : furnaces.entrySet()) {
            FurnaceData f = e.getValue();
            if (!f.hasContent()) {
                continue;
            }
            fAnc[fk] = e.getKey();
            for (int c = 0; c < FURN_SLOTS; c++) {
                fiI[fk * FURN_SLOTS + c] = f.inId[c];
                fiN[fk * FURN_SLOTS + c] = f.inN[c];
                foI[fk * FURN_SLOTS + c] = f.outId[c];
                foN[fk * FURN_SLOTS + c] = f.outN[c];
            }
            fk++;
        }
        s.furnaceAnchor = fAnc;
        s.fInItem = fiI;
        s.fInCount = fiN;
        s.fOutItem = foI;
        s.fOutCount = foN;
        return s;
    }

    /** 应用存档：重放世界改动 + 恢复玩家/背包/双条/时刻。 */
    public void applySave(GameSave s) {
        if (s.slot != null) {
            this.slot = s.slot;
        }
        if (s.saveName != null) {
            this.saveName = s.saveName;
        }
        edits.clear();
        mountEdits.clear();
        if (s.editIdx != null && s.editBlock != null) {
            int w = world.getWidth();
            for (int i = 0; i < s.editIdx.length && i < s.editBlock.length; i++) {
                int idx = s.editIdx[i], b = s.editBlock[i];
                int tx = idx % w, ty = idx / w;
                if (world.inBounds(tx, ty) && isValidBlock(b)) {   // 越界/未知 id 跳过（防崩）
                    world.setBlock(tx, ty, BlockType.of((short) b));
                    edits.put(idx, b);
                }
            }
        }
        player.setPos(s.playerX, s.playerY);
        ensureSupportedSpawn();   // 读档安全：地形因版本/生成漂移致脚下悬空或卡实体→弹回世界出生点（必在主岛上）
        inventory.loadFrom(s.invItem, s.invCount, s.invSelected);
        stats.setLucidity(s.lucidity);
        stats.setMana(s.mana);
        clock.setTotalMinutes(s.clockMinutes);
        coins = s.coins;
        if (s.servantCap > 0) {
            servantCap = s.servantCap;   // 存上限；仆从不存，重进需重新召唤
        }
        servants.clear();
        storyLog.clear();
        if (s.storyLog != null) {
            java.util.Collections.addAll(storyLog, s.storyLog);   // 恢复本梦的记忆
        }
        guide.setCodexOwned(s.codexOwned);   // 法典已购→不重复出售
        guide.setPresent(s.guidePresent);    // 构梦者一旦现身就常驻（不靠 coins 阈值重新判定）
        chests.clear();
        furnaces.clear();
        openFurnace = -1;
        if (s.chestTile != null && s.chestCap != null) {
            int off = 0;
            for (int j = 0; j < s.chestTile.length && j < s.chestCap.length; j++) {
                int cap = s.chestCap[j];
                int alloc = Math.max(1, Math.min(cap, 512));   // 异常容量钳制，防 OOM
                Inventory ch = new Inventory(alloc);
                ch.loadFrom(slice(s.chestItem, off, alloc), slice(s.chestCount, off, alloc), 0);
                chests.put(s.chestTile[j], ch);
                off += cap;   // 按原始 cap 前进，与序列化对齐
            }
        }
        if (s.paintItemId != null && s.paintColors != null && s.paintW != null && s.paintH != null
                && s.paintW.length == s.paintItemId.length && s.paintH.length == s.paintItemId.length) {
            int off = 0;
            for (int j = 0; j < s.paintItemId.length; j++) {
                int pw = s.paintW[j], ph = s.paintH[j];
                int len = pw * ph;
                if (pw <= 0 || ph <= 0 || len <= 0 || off + len > s.paintColors.length) {
                    break;   // 尺寸/数据不一致（旧档无 paintW 或损坏）→安全跳过
                }
                PaintedLook look = PaintedLook.forItem(pw, ph, slice(s.paintColors, off, len));
                applyItemPaint(s.paintItemId[j], look);
                off += len;
            }
        }
        if (s.mountIdx != null && s.mountBlock != null) {
            int mw = world.getWidth();
            for (int i = 0; i < s.mountIdx.length && i < s.mountBlock.length; i++) {
                int idx = s.mountIdx[i], mb = s.mountBlock[i];
                int tx = idx % mw, ty = idx / mw;
                if (world.inBounds(tx, ty) && isValidBlock(mb)) {
                    world.setMount(tx, ty, BlockType.of((short) mb));
                    mountEdits.put(idx, mb);
                }
            }
        }
        if (s.faceColors != null && s.faceColors.length == PaintedLook.W * PaintedLook.H) {
            appearance = new PaintedLook(s.faceTemplateId, s.faceColors);
            buildDreamer();                     // 读档按存档的上色重建外观纹理
        }
        if (s.furnaceAnchor != null) {
            int n = s.furnaceAnchor.length;
            for (int j = 0; j < n; j++) {
                FurnaceData f = new FurnaceData();
                int base = j * FURN_SLOTS;
                for (int c = 0; c < FURN_SLOTS; c++) {
                    int idx = base + c;
                    if (s.fInItem != null && idx < s.fInItem.length) {
                        f.inId[c] = s.fInItem[idx];
                    }
                    if (s.fInCount != null && idx < s.fInCount.length) {
                        f.inN[c] = s.fInCount[idx];
                    }
                    if (s.fOutItem != null && idx < s.fOutItem.length) {
                        f.outId[c] = s.fOutItem[idx];
                    }
                    if (s.fOutCount != null && idx < s.fOutCount.length) {
                        f.outN[c] = s.fOutCount[idx];
                    }
                    if (f.inId[c] < 0) {
                        f.inN[c] = 0;
                    }
                    if (f.outId[c] < 0) {
                        f.outN[c] = 0;
                    }
                }
                furnaces.put(s.furnaceAnchor[j], f);
            }
        }
        rebuildTreeRegistry();   // 依据已应用的 edits（含玩家种/砍的树格）重建树表
        growing.clear();
        if (s.growAnchor != null && s.growTargetH != null && s.growAge != null) {
            for (int j = 0; j < s.growAnchor.length
                    && j < s.growTargetH.length && j < s.growAge.length; j++) {
                if (trees.containsKey(s.growAnchor[j])) {
                    growing.put(s.growAnchor[j], new GrowState(s.growAge[j], s.growTargetH[j]));
                }
            }
        }
        lightDirty = true;
    }

    /**
     * 读档安全兜底：存档只存玩家坐标 + 改动 diff，基础地形每次按种子重生成；
     * 若世界生成器改版使主岛横向平移/地形变化，旧存档的玩家位置可能落在半空或嵌进实体。
     * 此时（卡进实体，或脚下 48 格内无地面）弹回本次生成的世界出生点（与主岛同锚点，必定站在岛上）。
     */
    private void ensureSupportedSpawn() {
        int bx = (int) Math.floor(player.centerX() / TILE);
        int by = (int) Math.floor(player.y() / TILE);
        boolean embedded = player.overlapsSolid(world);
        boolean groundBelow = false;
        for (int d = 0; d <= 48 && !groundBelow; d++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (world.isSolid(bx + dx, by - d)) {
                    groundBelow = true;
                    break;
                }
            }
        }
        if (embedded || !groundBelow) {
            player.setPos(spawnX, spawnY);
        }
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
        if (avatar != null) {
            avatar.setAppearance(appearance);   // 重绘/读档后同步蒙皮皮肤
        }
    }

    private static Pixmap lookPixmap(int[] colors) {
        return lookPixmap(PaintedLook.W, PaintedLook.H, colors);
    }

    private static Pixmap lookPixmap(int w, int h, int[] colors) {
        Pixmap pm = new Pixmap(w, h, Pixmap.Format.RGBA8888);
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
            pm.drawPixel(i % w, i / w);
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
        if (guideTex != null) {
            guideTex.dispose();
        }
        if (avatar != null) {
            avatar.dispose();
        }
    }
}
