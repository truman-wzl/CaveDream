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
import com.cavedream.core.render.UiIcons;
import com.cavedream.core.render.WoodUi;

/**
 * 选职业屏：复古木质公告牌里 5 个大正方形卡片，各显示该职业初始武器图标 + 名称。
 * ←/→ 或鼠标选择，Enter/点击确认 → 进入对应职业的新游戏。图标为程序化占位，后续可替换真图。
 */
public class ClassScreen extends ScreenAdapter implements Disposable {

    private final CaveDreamGame game;
    private final OrthographicCamera cam = new OrthographicCamera();
    private SpriteBatch batch;
    private Texture pixel;
    private BitmapFont titleFont;
    private BitmapFont labelFont;
    private UiIcons icons;
    private final GlyphLayout layout = new GlyphLayout();
    private int selected;

    public ClassScreen(CaveDreamGame game) {
        this.game = game;
        batch = new SpriteBatch();
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        titleFont = CjkFonts.get(40);
        labelFont = CjkFonts.get(20);
        icons = new UiIcons();
    }

    @Override
    public void resize(int width, int height) {
        cam.setToOrtho(false, width, height);
    }

    @Override
    public void show() {
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void render(float delta) {
        PlayerClass[] classes = PlayerClass.values();
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT)) {
            selected = (selected + classes.length - 1) % classes.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT)) {
            selected = (selected + 1) % classes.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            game.startNewDream(classes[selected]);
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.showTitle();
            return;
        }
        handleClick(classes);

        Gdx.gl.glClearColor(0.05f, 0.04f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        draw(classes);
        batch.end();
    }

    private void handleClick(PlayerClass[] classes) {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        float[] rect = cardRect(cam.viewportWidth, classes.length, 0);
        float size = rect[0];
        float gap = size * 0.25f;
        float totalW = classes.length * size + (classes.length - 1) * gap;
        float startX = (cam.viewportWidth - totalW) / 2f;
        float cy = cam.viewportHeight / 2f - size / 2f;
        float mx = Gdx.input.getX();
        float my = cam.viewportHeight - Gdx.input.getY();
        for (int i = 0; i < classes.length; i++) {
            float x = startX + i * (size + gap);
            if (mx >= x && mx <= x + size && my >= cy && my <= cy + size) {
                game.startNewDream(classes[i]);
                return;
            }
        }
    }

    private void draw(PlayerClass[] classes) {
        titleFont.setColor(0.95f, 0.9f, 0.7f, 1f);
        center(titleFont, "选择你的做梦人", cam.viewportWidth / 2f, cam.viewportHeight - 70);
        labelFont.setColor(0.8f, 0.75f, 0.6f, 1f);
        center(labelFont, "← → 选择   Enter 确认   Esc 返回", cam.viewportWidth / 2f, 40);

        float[] rect = cardRect(cam.viewportWidth, classes.length, 0);
        float size = rect[0];
        float gap = size * 0.25f;
        float totalW = classes.length * size + (classes.length - 1) * gap;
        float startX = (cam.viewportWidth - totalW) / 2f;
        float cy = cam.viewportHeight / 2f - size / 2f;

        for (int i = 0; i < classes.length; i++) {
            float x = startX + i * (size + gap);
            WoodUi.card(batch, pixel, x, cy, size, i == selected, i == selected);
            Texture icon = icons.weapon(classes[i]);
            float is = size * 0.5f;
            batch.setColor(1, 1, 1, 1);
            batch.draw(icon, x + size / 2f - is / 2f, cy + size * 0.42f, is, is);
            labelFont.setColor(i == selected ? Color.WHITE : new Color(0.8f, 0.75f, 0.6f, 1f));
            center(labelFont, classes[i].cn(), x + size / 2f, cy + size * 0.16f);
        }
    }

    /** 卡片尺寸随屏宽/数量自适应。 */
    private float[] cardRect(float viewportW, int n, float ignored) {
        float size = Math.min(viewportW / (n * 1.5f), 180f);
        return new float[]{size};
    }

    private void center(BitmapFont font, String s, float cx, float baseY) {
        layout.setText(font, s);
        font.draw(batch, s, cx - layout.width / 2f, baseY);
    }

    @Override
    public void dispose() {
        batch.dispose();
        pixel.dispose();
        icons.dispose();
    }
}
