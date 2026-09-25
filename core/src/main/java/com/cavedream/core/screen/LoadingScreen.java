package com.cavedream.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.CaveDreamGame;
import com.cavedream.core.player.PlayerClass;
import com.cavedream.core.render.PaintedLook;
import com.cavedream.core.render.WoodUi;
import com.cavedream.core.save.GameSave;
import com.cavedream.core.world.gen.GeneratedWorld;
import com.cavedream.core.world.gen.LayerLayout;
import com.cavedream.core.world.gen.WorldGenerator;

/**
 * 世界生成加载屏（泰拉瑞亚式）：后台线程生成中世界，前台进度条 + 底部滚动风味文案；
 * 完成即构建 PlayScreen 进入世界。若带存档（继续游戏），进入后套用存档。
 */
public class LoadingScreen extends ScreenAdapter implements Disposable {

    private static final String[] FLAVOR = {
            "正在凝结大地……",
            "唤醒沉睡的矿脉……",
            "让树木与花草生长……",
            "点亮地底的萤火与幻菇……",
            "在两侧注入梦之海……",
            "把地狱压到最底层……",
            "安放梦锚柱与宝箱……",
            "搅拌混沌——别催，梦快醒了……",
    };

    private final CaveDreamGame game;
    private final PlayerClass playerClass;
    private final long seed;
    private final GameSave save;          // 可空（新游戏）；继续游戏时携带
    private final String slot;            // 存档槽位（新游戏分配的新档名 / 继续时为原档名）
    private final PaintedLook appearance; // 捏脸上色外观（新游戏来自画板；继续时由 applySave 覆盖）
    private final String saveName;        // 新游戏存档显示名（创建时命名）

    private final OrthographicCamera cam = new OrthographicCamera();
    private SpriteBatch batch;
    private Texture pixel;
    private BitmapFont titleFont;
    private BitmapFont font;
    private final GlyphLayout measure = new GlyphLayout();

    private volatile GeneratedWorld result;
    private volatile String error;
    private boolean started;
    private boolean handed;
    private float progress;
    private float flavorT;
    private int flavorIdx;

    public LoadingScreen(CaveDreamGame game, PlayerClass playerClass, long seed, GameSave save, String slot,
                         PaintedLook appearance, String saveName) {
        this.game = game;
        this.playerClass = playerClass;
        this.seed = seed;
        this.save = save;
        this.slot = slot;
        this.appearance = appearance != null ? appearance : new PaintedLook();
        this.saveName = saveName == null ? "" : saveName;
        batch = new SpriteBatch();
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        titleFont = CjkFonts.get(48);
        font = CjkFonts.get(20);
    }

    @Override
    public void resize(int width, int height) {
        cam.setToOrtho(false, width, height);
    }

    @Override
    public void show() {
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (!started) {
            started = true;
            new Thread(this::generate, "cavedream-worldgen").start();
        }
    }

    private void generate() {
        try {
            result = WorldGenerator.generate(LayerLayout.l1(), seed);
        } catch (Throwable t) {
            error = t.getClass().getSimpleName() + ": " + t.getMessage();
        }
    }

    @Override
    public void render(float delta) {
        flavorT += delta;
        if (flavorT > 1.7f) {
            flavorT = 0f;
            flavorIdx++;
        }
        if (error != null) {
            progress = 0f;
        } else if (result == null) {
            progress = Math.min(0.86f, progress + delta * 0.5f);
        } else {
            progress = Math.min(1f, progress + delta * 2.5f);
        }

        Gdx.gl.glClearColor(0.03f, 0.03f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        float w = cam.viewportWidth, h = cam.viewportHeight;
        titleFont.setColor(0.92f, 0.88f, 1f, 1f);
        center(titleFont, "梦迹行者", w / 2f, h * 0.62f);
        titleFont.setColor(1, 1, 1, 1);

        float barW = Math.min(560f, w * 0.6f), barH = 26f;
        float bx = w / 2f - barW / 2f, by = h * 0.42f;
        WoodUi.panel(batch, pixel, bx - 8, by - 8, barW + 16, barH + 16);
        batch.setColor(0.05f, 0.05f, 0.1f, 1f);
        batch.draw(pixel, bx, by, barW, barH);
        batch.setColor(0.55f, 0.75f, 1f, 1f);
        batch.draw(pixel, bx, by, barW * progress, barH);
        batch.setColor(1, 1, 1, 1);
        font.setColor(0.85f, 0.88f, 1f, 1f);
        center(font, (int) (progress * 100) + "%", w / 2f, by - 30);
        String line = error != null ? "生成出错：" + error : FLAVOR[flavorIdx % FLAVOR.length];
        font.setColor(error != null ? Color.ORANGE : new Color(0.7f, 0.72f, 0.85f, 1f));
        center(font, line, w / 2f, h * 0.2f);
        font.setColor(1, 1, 1, 1);
        batch.end();

        if (error != null && !handed) {
            handed = true;
            game.showTitle();
        } else if (result != null && progress >= 1f && !handed) {
            handed = true;
            enterWorld(result);
        }
    }

    private void enterWorld(GeneratedWorld gw) {
        PlayScreen ps = new PlayScreen(game, playerClass, gw.world(), seed, gw.spawn().x(), gw.spawn().y(), appearance);
        if (save != null) {
            ps.applySave(save);        // 沿用原档槽位
        } else {
            ps.setSlot(slot);          // 新游戏写入分配的新槽
            ps.setSaveName(saveName);  // 新游戏写入创建时的命名
        }
        game.setScreen(ps);
    }

    private void center(BitmapFont f, String s, float cx, float baseY) {
        measure.setText(f, s);
        f.draw(batch, s, cx - measure.width / 2f, baseY);
    }

    @Override
    public void dispose() {
        batch.dispose();
        pixel.dispose();
    }
}
