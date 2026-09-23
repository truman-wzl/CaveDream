package com.cavedream.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.CaveDreamGame;
import com.cavedream.core.light.GameClock;
import com.cavedream.core.light.LightEngine;
import com.cavedream.core.light.LightSource;
import com.cavedream.core.light.SunShadow;
import com.cavedream.core.render.BlockTextures;
import com.cavedream.core.render.ItemCatalog;
import com.cavedream.core.render.SkyRenderer;
import com.cavedream.core.save.GameSave;
import com.cavedream.core.inventory.Inventory;
import com.cavedream.core.item.Item;
import com.cavedream.core.player.PlayerClass;
import com.cavedream.core.player.PlayerStats;
import com.cavedream.core.render.UiIcons;
import com.cavedream.core.render.WoodUi;
import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.ItemDrop;
import com.cavedream.core.world.LayerWorld;
import com.cavedream.core.world.PlayerEntity;
import com.cavedream.core.world.Tool;
import com.cavedream.core.world.mob.Mob;
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
    private static final float PICKUP_MAGNET_PX = 1.6f * TILE;   // 拾取吸附半径（px）
    private static final float MAGNET_SPEED = 260f;             // 吸附飞行速度

    /** 拾取动画残影：从掉落点飞向玩家、逐渐缩小淡出。 */
    private static final class PickupFx {
        float fromX, fromY, t, life;
        Item item;
        PickupFx(float x, float y, Item it) { fromX = x; fromY = y; item = it; life = 0.28f; }
    }
    private static final int PLAYER_GLOW_LEVEL = 12;    // 角色自带微光峰值亮度 0~15
    private static final float PLAYER_GLOW_RADIUS = 6.5f;   // 角色微光半径（格）
    private static final int SUN_SHADOW_DIST = 14;      // 太阳投影射线最大步数（格）

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
    private TextureRegion blobRegion;
    private TextureRegion pickaxeRegion;
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
    private int daylight0to15 = LightEngine.MAX_LEVEL;   // 当前天光强度
    private float sunX, sunY = 1f;                        // 指向太阳的单位向量

    // —— 背包 / 挖掘 / 动画 ——
    private static final int HOTBAR = 10;                // 快捷栏格数（背包前 10 格，GDD §3.5）
    private static final int INV_TOTAL = 40;             // 背包总格（含快捷栏）：GDD §3.5 初始 40
    private final Inventory inventory = new Inventory(INV_TOTAL);
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
    private float invuln;                                                    // 接触伤害无敌帧
    private float attackCd;                                                   // 攻击冷却
    private boolean showInv;                                                  // E 开合背包
    private static final float SWING_DUR = 0.28f;                            // 一次挥击时长（秒）
    private float swingT;                                                     // 挥击进度 0~1
    private boolean swinging;                                                 // 正在挥击
    // —— 战斗闭环 / 存档 ——
    private final java.util.HashMap<Integer, Integer> edits = new java.util.HashMap<>();   // 改动格 idx→方块id
    private int coins;                                                        // 铸梦币
    private boolean downed;                                                   // 濒死态
    private float downedT;                                                    // 濒死倒计时（秒）
    private float spawnX, spawnY;                                             // 出生点（溃梦复活）
    // —— 世界/存档/暂停 ——
    private final long seed;                                                  // 世界种子（存档用）
    private static final int LIGHT_RADIUS = 90;                               // 局部光照重算半径（格）
    private int lastLCx = Integer.MIN_VALUE, lastLCy;                         // 上次光照重算中心
    private float saveTimer;                                                  // 自动存档计时
    private boolean paused;                                                   // ESC 暂停菜单
    private String toast;                                                     // 顶部提示（如已存档）
    private float toastT;

    public PlayScreen(CaveDreamGame game, PlayerClass playerClass, LayerWorld world,
                      long seed, int spawnTileX, int spawnTileY) {
        this.game = game;
        this.playerClass = playerClass;
        this.stats = new PlayerStats(playerClass);
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
        font = CjkFonts.create(15);   // HUD 参数字体缩小（开发者信息保留但不占屏）
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
        dreamerRegion = new TextureRegion(textures.dreamer());
        pickaxeRegion = new TextureRegion(textures.pickaxe());
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
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void resize(int width, int height) {
        float aspect = width / (float) Math.max(1, height);
        camera.viewportHeight = VIEW_HEIGHT_PX;
        camera.viewportWidth = VIEW_HEIGHT_PX * aspect;
        uiCam.setToOrtho(false, width, height);   // UI：1 单位 = 1 屏幕像素，左下原点
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
            float[] sd = clock.sunDirection();
            sunX = sd[0];
            sunY = sd[1];
            ensureLighting();
            rebuildDynamicLights();
            updateDrops(delta);
            stats.update(delta);
            updateMobs(delta);
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
        sky.draw(batch, Gdx.graphics.getWidth(), Gdx.graphics.getHeight(),
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
        drawPickupFx();
        drawDust();
        drawCursor();
        batch.end();
        drawHud();
        drawHotbar();
        drawStats();
        drawInventory();
        drawToast();
    }

    /** 光照：脏（挖/放）或玩家移到新区域时，按玩家周围窗口局部重算。 */
    private void ensureLighting() {
        int cx = (int) Math.floor(player.centerX() / TILE);
        int cy = (int) Math.floor(player.centerY() / TILE);
        if (lightDirty || Math.abs(cx - lastLCx) > LIGHT_RADIUS / 2 || Math.abs(cy - lastLCy) > LIGHT_RADIUS / 2) {
            lightEngine.recomputeRegion(world, cx, cy, LIGHT_RADIUS);
            lastLCx = cx;
            lastLCy = cy;
            lightDirty = false;
        }
    }

    private void saveNow() {
        game.saveGame(toSave());
        toast = "已存档";
        toastT = 2f;
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
        float mx = Gdx.input.getX(), my = uiCam.viewportHeight - Gdx.input.getY();
        float cx = pauseBtnX(), top = pauseBtnTop(), bw = 260f, bh = 54f, gap = 16f;
        for (int i = 0; i < PAUSE_OPTS.length; i++) {
            float y = top - (i + 1) * (bh + gap) + gap;
            if (mx >= cx && mx <= cx + bw && my >= y && my <= y + bh) {
                if (i == 0) {
                    paused = false;
                } else if (i == 1) {
                    saveNow();
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
        float mx = Gdx.input.getX(), my = uiCam.viewportHeight - Gdx.input.getY();
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
        float x = 16f, top = uiCam.viewportHeight - 16f;
        int moons = (int) Math.ceil(stats.maxLucidity() / 10f);
        drawPips(uiIcons.moon(), x, top, moons, stats.lucidity() / 10f);
        int stars = (int) Math.ceil(stats.maxMana() / 10f);
        drawPips(uiIcons.star(), x, top - 30f, stars, stats.mana() / 10f);
        font.setColor(0.9f, 0.92f, 1f, 1f);
        font.draw(batch, stats.lucidity() + "/" + stats.maxLucidity(), x + moons * 26f + 8, top - 6);
        font.draw(batch, stats.mana() + "/" + stats.maxMana(), x + stars * 26f + 8, top - 36);
        font.setColor(1, 1, 1, 1);
        // 铸梦币 + 操作提示（右上）
        font.setColor(1f, 0.9f, 0.4f, 1f);
        font.draw(batch, "铸梦币 " + coins, uiCam.viewportWidth - 130, uiCam.viewportHeight - 24);
        font.setColor(0.8f, 0.82f, 0.9f, 0.9f);
        font.draw(batch, "E 背包・F5 存档・左键使用・右键放置", uiCam.viewportWidth - 300, uiCam.viewportHeight - 46);
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

    /** 一排图标，第 i 个的 alpha = 该格已填充比例（实现“由浅变深/缺则透明”）。 */
    private void drawPips(Texture tex, float x, float y, int count, float value) {
        float size = 22f, step = 26f;
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.E)) {
            showInv = !showInv;
        }
        if (showInv) {                       // 背包开启：只处理选格，不挖掘/放置/攻击
            miningNow = false;
            digProgress = 0f;
            if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                int idx = invSlotAtMouse();
                if (idx >= 0) {
                    inventory.select(idx);
                }
            }
            return;
        }
        if (downed) {                          // 濒死：不可挖掘/攻击/放置（仅可移动，移速已降）
            miningNow = false;
            return;
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

        // 使用手持物：按下即起一次挥击（任何物品可空挥，动画驱动，为后续技能/特效留接口）
        if (leftPressed) {
            swinging = true;
            swingT = 0f;
        }
        if (swinging) {
            swingT += frameDelta / SWING_DUR;
            if (swingT >= 1f) {
                swingT = 0f;
                swinging = false;
            }
        }

        // 攻击：挥击命中目标格上的怪（受冷却）；空挥也进冷却
        if (leftPressed && attackCd <= 0f) {
            Mob hit = tile != null ? mobAt(tile[0], tile[1]) : null;
            if (hit != null && hit.damage(weaponDamage)) {
                coins += 1 + (int) (Math.random() * 4);   // 击杀掉落铸梦币
            }
            attackCd = 0.4f;
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
                batch.draw(textures.grassLip(), px, py + TILE - 3, TILE, 8);   // 草唇（一排草叶）
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
                    fill(x * (float) TILE, y * (float) TILE, TILE, TILE, 0.02f, 0.03f, 0.07f, dark);
                }
            }
        }
        batch.setColor(1, 1, 1, 1);
    }

    /** 该格暗度 0~1（=1-亮度/15）；亮度受环境天光/块光与角色、特效等动态光共同影响。 */
    private float darknessAt(int x, int y) {
        float a = 1f - lightLevelAt(x, y) / (float) LightEngine.MAX_LEVEL;
        return a > 0.98f ? 0.98f : a;   // 最暗近乎全黑但留一丝轮廓
    }

    /** 该格合成亮度 0~15（对外 1~16 级 = +1）：天光经太阳方向投影，块光/角色光不受太阳遮挡。 */
    private int lightLevelAt(int x, int y) {
        int skyGeo = lightEngine.skyAt(x, y);
        float sunVis = daylight0to15 > 0
                ? SunShadow.visibility(world, x, y, sunX, sunY, SUN_SHADOW_DIST) : 1f;
        int skyLight = Math.round(skyGeo / (float) LightEngine.MAX_LEVEL * daylight0to15 * sunVis);
        int lvl = Math.max(skyLight, lightEngine.blockAt(x, y));
        for (int i = 0, n = dynamicLights.size(); i < n; i++) {
            int c = dynamicLights.get(i).at(x, y);
            if (c > lvl) {
                lvl = c;
            }
        }
        return lvl;
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
        // 手持镐（先画身体压在柄上更像握持）；挖掘时绕手挥动
        // 手持物挥击（任何物品左键都挥，含空挥）：swingT 驱动 0→1→0 的弧
        float swing = swinging ? (float) Math.sin(swingT * Math.PI) : 0f;
        float pickAng = (facing > 0 ? -30f : 30f) + swing * 80f * (facing > 0 ? 1 : -1);
        float hx = player.centerX() + facing * w * 0.5f;
        float hy = player.centerY() + bob;
        float ps = 26f;
        Item held = inventory.selectedItem();
        TextureRegion hand = held != null ? itemSprite(held) : pickaxeRegion;
        batch.draw(hand, hx - ps / 2, hy - ps / 2, ps / 2, ps / 2, ps, ps, facing, 1, pickAng);
        // 侧脸身体贴图默认朝右；scaleX=facing 镜像，tilt 为小幅旋转
        batch.draw(dreamerRegion, player.x(), player.y() + bob, w / 2, h / 2, w, h, facing, 1, tilt);
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
            if (d.overlapsRect(player.x(), player.y(), player.width(), player.height())
                    && inventory.add(d.item(), d.count())) {
                pickupFx.add(new PickupFx(d.centerX(), d.centerY(), d.item()));
                drops.remove(i);
                continue;
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

    /** 刷怪推进 + 接触伤害 + 溃梦复活。 */
    private void updateMobs(float dt) {
        invuln = Math.max(0f, invuln - dt);
        attackCd = Math.max(0f, attackCd - dt);
        spawner.update(world, player.centerX(), player.centerY(), clock.isNight(), dt);
        if (invuln <= 0f) {
            for (Mob m : spawner.mobs()) {
                if (m.overlapsRect(player.x(), player.y(), player.width(), player.height())) {
                    stats.damage(m instanceof Slime ? ((Slime) m).touchDamage() : 5);
                    invuln = 0.8f;
                    break;
                }
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
        if (it.placeable()) {
            return tileRegion(it.block(), 0, 0);
        }
        Texture t = uiIcons.iconFor(it);
        return t != null ? new TextureRegion(t) : pickaxeRegion;
    }

    /** 背包一行的列数与单格尺寸（相对窗口）。 */
    private static final int INV_COLS = 10;

    private float invSlotSize() {
        return uiCam.viewportHeight * 0.055f;
    }

    /** 第 i 格的 {x,y,size}（屏幕坐标，左上原点换算前的左下 y）。 */
    private float[] invSlotRect(int i) {
        float slot = invSlotSize(), gap = slot * 0.12f;
        int rows = (INV_TOTAL + INV_COLS - 1) / INV_COLS;
        float gridW = INV_COLS * slot + (INV_COLS - 1) * gap;
        float gridH = rows * slot + (rows - 1) * gap;
        float startX = (uiCam.viewportWidth - gridW) / 2f;
        float topY = uiCam.viewportHeight / 2f + gridH / 2f;
        int r = i / INV_COLS, c = i % INV_COLS;
        float x = startX + c * (slot + gap);
        float y = topY - (r + 1) * slot - r * gap;
        return new float[]{x, y, slot};
    }

    /** 鼠标命中的背包格序号；无则 -1。 */
    private int invSlotAtMouse() {
        float mx = Gdx.input.getX(), my = uiCam.viewportHeight - Gdx.input.getY();
        for (int i = 0; i < INV_TOTAL; i++) {
            float[] r = invSlotRect(i);
            if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[2]) {
                return i;
            }
        }
        return -1;
    }

    /** 背包面板（E 开合）：木质公告牌 + 18 格，前 10 为快捷栏。 */
    private void drawInventory() {
        if (!showInv) {
            return;
        }
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        float slot = invSlotSize(), gap = slot * 0.12f;
        int rows = (INV_TOTAL + INV_COLS - 1) / INV_COLS;
        float gridW = INV_COLS * slot + (INV_COLS - 1) * gap;
        float gridH = rows * slot + (rows - 1) * gap;
        float pad = slot * 0.5f;
        WoodUi.panel(batch, pixel, (uiCam.viewportWidth - gridW) / 2f - pad,
                uiCam.viewportHeight / 2f - gridH / 2f - pad, gridW + 2 * pad, gridH + 2 * pad);
        for (int i = 0; i < INV_TOTAL; i++) {
            float[] r = invSlotRect(i);
            float x = r[0], y = r[1], s = r[2];
            fill(x, y, s, s, 0f, 0f, 0f, 0.4f);
            Item it = inventory.itemAt(i);
            if (it != null) {
                batch.setColor(1, 1, 1, 1);
                batch.draw(itemSprite(it), x + s * 0.09f, y + s * 0.09f, s * 0.82f, s * 0.82f);
                if (inventory.countAt(i) > 1) {
                    font.draw(batch, String.valueOf(inventory.countAt(i)), x + s * 0.12f, y + s * 0.9f);
                }
            }
            if (i == inventory.selected()) {
                batch.setColor(1f, 1f, 0.4f, 1f);
                batch.draw(pixel, x - 1, y - 1, s + 2, 2f);
                batch.draw(pixel, x - 1, y + s - 1, s + 2, 2f);
                batch.draw(pixel, x - 1, y - 1, 2f, s + 2);
                batch.draw(pixel, x + s - 1, y - 1, 2f, s + 2);
                batch.setColor(1, 1, 1, 1);
            }
        }
        font.setColor(0.9f, 0.9f, 1f, 1f);
        font.draw(batch, "背包（E 关闭，左键选格）", (uiCam.viewportWidth - gridW) / 2f,
                uiCam.viewportHeight / 2f + gridH / 2f + pad);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** 快捷栏（屏幕左下，相对尺寸）：逐格画物品图标+数量，高亮当前格，旁标工具名。 */
    private void drawHotbar() {
        batch.setProjectionMatrix(uiCam.combined);
        batch.begin();
        int n = Math.min(HOTBAR, inventory.size());
        float slot = uiCam.viewportHeight * 0.06f;      // 相对窗口高度（始终占固定比例）
        float gap = slot * 0.12f;
        float x0 = slot * 0.4f, y0 = slot * 0.4f;
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
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        int px = (int) Math.floor(player.centerX() / TILE);
        int py = (int) Math.floor(player.centerY() / TILE);
        font.setColor(0.85f, 0.88f, 1f, 1f);
        font.draw(batch,
                "L1 浅梦 · 时刻 " + clock.format() + " · 天光 " + clock.skyLevel1to16()
                        + "/16 · 我处亮度 " + (lightLevelAt(px, py) + 1)
                        + " · 帧率 " + Gdx.graphics.getFramesPerSecond()
                        + " · 手持：" + holdBlock.cn()
                        + " · A/D 移动  W 跳跃  左键挖掘  右键放置  ESC 返回标题",
                camera.position.x - camera.viewportWidth / 2 + 12,
                camera.position.y + camera.viewportHeight / 2 - 16);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** 导出当前世界为存档数据（改动 diff + 玩家状态）。 */
    public GameSave toSave() {
        GameSave s = new GameSave();
        s.className = playerClass.name();
        s.seed = (int) seed;
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
        return s;
    }

    /** 应用存档：重放世界改动 + 恢复玩家/背包/双条/时刻。 */
    public void applySave(GameSave s) {
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
        lightDirty = true;
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        pixel.dispose();
        textures.dispose();
        uiIcons.dispose();
        sky.dispose();
    }
}
