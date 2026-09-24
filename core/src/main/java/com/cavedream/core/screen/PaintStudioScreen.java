package com.cavedream.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import com.cavedream.core.render.Palette;
import com.cavedream.core.render.WoodUi;

/**
 * 捏脸画板（“灵魂画师”小游戏）：引擎给出带部位划分的白色底模，玩家从全局 32 色中选色逐格涂，
 * 成品即玩家/NPC 外观（{@link PaintedLook}）。选职业后、世界生成前进入；也可由剧情触发复用。
 */
public class PaintStudioScreen extends ScreenAdapter implements Disposable {

    private static final int MODE_PEN = 0, MODE_ERASE = 1, MODE_FILL = 2;
    private static final String[] MODE_NAMES = {"画笔", "橡皮", "填块"};

    private final CaveDreamGame game;
    private final PlayerClass playerClass;
    private final PaintedLook look = new PaintedLook();

    private final OrthographicCamera cam = new OrthographicCamera();
    private SpriteBatch batch;
    private Texture pixel;
    private BitmapFont titleFont;
    private BitmapFont font;
    private BitmapFont smallFont;
    private final GlyphLayout layout = new GlyphLayout();

    private int colorIdx = 7;        // 当前调色板色（默认衣袍紫）
    private int mode = MODE_PEN;
    private boolean dragging;
    private float showT;              // 入场计时：屏蔽上一屏漏进来的点击/回车

    // 布局缓存（render 计算、输入用）
    private float cell, canvasX, canvasY, canvasW, canvasH;
    private float palX, palY, palCell;
    private final float[] btnY = new float[4];
    private float btnX, btnW, btnH;
    private float doneX, doneY, doneW, doneH;
    private static final int PAL_COLS = 10;                 // 调色板每行色格数（行数按 SIZE 自适应）

    public PaintStudioScreen(CaveDreamGame game, PlayerClass playerClass) {
        this.game = game;
        this.playerClass = playerClass;
        batch = new SpriteBatch();
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        titleFont = CjkFonts.get(34);
        font = CjkFonts.get(18);
        smallFont = CjkFonts.get(13);
    }

    @Override
    public void resize(int width, int height) {
        cam.setToOrtho(false, width, height);
        computeLayout();
    }

    @Override
    public void show() {
        showT = 0f;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    private void computeLayout() {
        float w = cam.viewportWidth, h = cam.viewportHeight;
        cell = Math.min(h * 0.72f / PaintedLook.H, w * 0.4f / PaintedLook.W);
        canvasW = cell * PaintedLook.W;
        canvasH = cell * PaintedLook.H;
        canvasX = w * 0.08f;
        canvasY = h / 2f - canvasH / 2f;
        // 调色板：PAL_COLS 列×自适应行（黑白灰阶 + 彩虹）
        palCell = Math.min(w * 0.032f, h * 0.05f);
        int rows = (Palette.SIZE + PAL_COLS - 1) / PAL_COLS;
        float palW = palCell * PAL_COLS;
        palX = canvasX + canvasW + w * 0.05f;
        palY = h * 0.76f;
        // 模式/重置按钮（调色板下方）
        btnX = palX;
        btnW = Math.min(palW, w * 0.15f);
        btnH = h * 0.05f;
        float by = palY - rows * palCell - h * 0.03f;
        for (int i = 0; i < 4; i++) {
            btnY[i] = by - i * (btnH + h * 0.012f);
        }
        doneW = btnW;
        doneH = h * 0.06f;
        doneX = btnX;
        doneY = btnY[3] - doneH - h * 0.02f;
    }

    @Override
    public void render(float delta) {
        showT += delta;
        boolean ready = showT > 0.25f;          // 入场 0.25s 内不响应输入，避免选职业那一下漏进来秒“完成”
        if (!ready) {
            paintFrame();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.showTitle();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            mode = MODE_PEN;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            mode = MODE_ERASE;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            mode = MODE_FILL;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            look.reset();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            game.startNewDream(playerClass, look);
            return;
        }
        handlePointer();

        paintFrame();
    }

    private void paintFrame() {
        Gdx.gl.glClearColor(0.05f, 0.04f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        draw();
        batch.end();
    }

    private void handlePointer() {
        float mx = Gdx.input.getX();
        float my = cam.viewportHeight - Gdx.input.getY();
        boolean just = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        boolean held = Gdx.input.isButtonPressed(Input.Buttons.LEFT);

        if (just) {
            // 调色板
            int sw = hitPalette(mx, my);
            if (sw >= 0) {
                colorIdx = sw;
                mode = MODE_PEN;                 // 选色即回到画笔
                return;
            }
            // 按钮：模式×3 + 重置
            for (int i = 0; i < 4; i++) {
                if (inRect(mx, my, btnX, btnY[i], btnW, btnH)) {
                    if (i < 3) {
                        mode = i;
                    } else {
                        look.reset();
                    }
                    return;
                }
            }
            // 完成
            if (inRect(mx, my, doneX, doneY, doneW, doneH)) {
                game.startNewDream(playerClass, look);
                return;
            }
            // 画布：开始涂
            if (inRect(mx, my, canvasX, canvasY, canvasW, canvasH)) {
                dragging = true;
                paintAt(mx, my);
                return;
            }
            dragging = false;
        } else if (held && dragging) {
            paintAt(mx, my);
        } else if (!held) {
            dragging = false;
        }
    }

    private void paintAt(float mx, float my) {
        int gx = (int) ((mx - canvasX) / cell);
        int gy = PaintedLook.H - 1 - (int) ((my - canvasY) / cell);   // 数据 y 向下
        if (gx < 0 || gy < 0 || gx >= PaintedLook.W || gy >= PaintedLook.H) {
            return;
        }
        if (mode == MODE_PEN) {
            look.paint(gx, gy, Palette.COLORS[colorIdx]);
        } else if (mode == MODE_ERASE) {
            look.erase(gx, gy);
        } else {
            byte r = look.regions()[gy * PaintedLook.W + gx];
            if (r != PaintedLook.R_OUT) {
                look.fillRegion(r, Palette.COLORS[colorIdx]);
            }
        }
    }

    private int hitPalette(float mx, float my) {
        for (int i = 0; i < Palette.SIZE; i++) {
            int col = i % PAL_COLS, row = i / PAL_COLS;
            float x = palX + col * palCell, y = palY - (row + 1) * palCell;
            if (inRect(mx, my, x, y, palCell, palCell)) {
                return i;
            }
        }
        return -1;
    }

    private void draw() {
        float w = cam.viewportWidth, h = cam.viewportHeight;
        titleFont.setColor(0.95f, 0.9f, 0.72f, 1f);
        center(titleFont, "描绘你的梦之躯", w / 2f, h - 46);
        titleFont.setColor(1, 1, 1, 1);
        font.setColor(0.7f, 0.72f, 0.85f, 1f);
        center(font, playerClass.cn() + "　·　整幅画布自由上色（黑白 · 彩虹）", palX + palCell * (PAL_COLS / 2f), h - 46);
        font.setColor(1, 1, 1, 1);

        drawCanvas();
        drawPalette();
        drawButtons();

        smallFont.setColor(0.6f, 0.62f, 0.75f, 1f);
        center(smallFont, "1 画笔　2 橡皮　3 填块　R 重置　Enter 完成并进入梦境　Esc 返回", w / 2f, 34);
        smallFont.setColor(1, 1, 1, 1);
    }

    private void drawCanvas() {
        // 木质边框
        WoodUi.panel(batch, pixel, canvasX - 10, canvasY - 10, canvasW + 20, canvasH + 20);
        int[] c = look.colors;
        for (int y = 0; y < PaintedLook.H; y++) {
            for (int x = 0; x < PaintedLook.W; x++) {
                int v = c[y * PaintedLook.W + x];
                float px = canvasX + x * cell;
                float py = canvasY + (PaintedLook.H - 1 - y) * cell;
                // 全黑底、非黑为人（纯黑=透明键）；直接按颜色画
                batch.setColor(((v >> 16) & 0xFF) / 255f, ((v >> 8) & 0xFF) / 255f, (v & 0xFF) / 255f, 1f);
                batch.draw(pixel, px, py, cell, cell);
            }
        }
        // 淡网格线（在黑底上标出格子，便于对齐造型）
        batch.setColor(1f, 1f, 1f, 0.06f);
        for (int x = 0; x <= PaintedLook.W; x++) {
            batch.draw(pixel, canvasX + x * cell, canvasY, 1f, canvasH);
        }
        for (int y = 0; y <= PaintedLook.H; y++) {
            batch.draw(pixel, canvasX, canvasY + y * cell, canvasW, 1f);
        }
        batch.setColor(1, 1, 1, 1);
    }

    private void drawPalette() {
        for (int i = 0; i < Palette.SIZE; i++) {
            int col = i % PAL_COLS, row = i / PAL_COLS;
            float x = palX + col * palCell, y = palY - (row + 1) * palCell;
            int v = Palette.COLORS[i];
            batch.setColor(((v >> 16) & 0xFF) / 255f, ((v >> 8) & 0xFF) / 255f, (v & 0xFF) / 255f, 1f);
            batch.draw(pixel, x + 1, y + 1, palCell - 2, palCell - 2);
            batch.setColor(1, 1, 1, 1);
            if (i == colorIdx) {
                border(x, y, palCell, palCell, 0.9f, 0.85f, 0.4f);
            }
        }
        font.setColor(0.85f, 0.88f, 1f, 1f);
        font.draw(batch, "当前色", palX, palY + 24);
        batch.setColor(1, 1, 1, 1);
        font.setColor(1, 1, 1, 1);
    }

    private void drawButtons() {
        for (int i = 0; i < 3; i++) {
            WoodUi.plank(batch, pixel, btnX, btnY[i], btnW, btnH, mode == i);
            font.setColor(mode == i ? Color.WHITE : new Color(0.8f, 0.75f, 0.6f, 1f));
            center(font, MODE_NAMES[i], btnX + btnW / 2f, btnY[i] + btnH * 0.35f);
            font.setColor(1, 1, 1, 1);
        }
        WoodUi.plank(batch, pixel, btnX, btnY[3], btnW, btnH, false);
        font.setColor(0.8f, 0.75f, 0.6f, 1f);
        center(font, "重置", btnX + btnW / 2f, btnY[3] + btnH * 0.35f);
        font.setColor(1, 1, 1, 1);

        WoodUi.plank(batch, pixel, doneX, doneY, doneW, doneH, true);
        font.setColor(1f, 0.95f, 0.7f, 1f);
        center(font, "完成 · 入梦", doneX + doneW / 2f, doneY + doneH * 0.4f);
        font.setColor(1, 1, 1, 1);
    }

    private void border(float x, float y, float w, float h, float r, float g, float b) {
        batch.setColor(r, g, b, 1f);
        batch.draw(pixel, x, y, w, 2f);
        batch.draw(pixel, x, y + h - 2, w, 2f);
        batch.draw(pixel, x, y, 2f, h);
        batch.draw(pixel, x + w - 2, y, 2f, h);
        batch.setColor(1, 1, 1, 1);
    }

    private static boolean inRect(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private void center(BitmapFont f, String s, float cx, float baseY) {
        layout.setText(f, s);
        f.draw(batch, s, cx - layout.width / 2f, baseY);
    }

    @Override
    public void dispose() {
        batch.dispose();
        pixel.dispose();
    }
}
