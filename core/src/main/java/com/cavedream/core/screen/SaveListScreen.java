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
import com.cavedream.core.render.WoodUi;
import com.cavedream.core.save.GameSave;

import java.util.List;

/**
 * 存档列表屏（主菜单“继续游戏”进入）：列出本地已游玩存档，点选一个进入（加载屏按种子重建）。
 * 每行摘要：职业 · 铸梦币 · 时刻 · 梦眠。Esc/返回按钮回主菜单。
 */
public class SaveListScreen extends ScreenAdapter implements Disposable {

    private final CaveDreamGame game;
    private final OrthographicCamera cam = new OrthographicCamera();
    private SpriteBatch batch;
    private Texture pixel;
    private BitmapFont titleFont;
    private BitmapFont font;
    private final GlyphLayout measure = new GlyphLayout();
    private List<GameSave> saves;
    private int hover = -1;

    public SaveListScreen(CaveDreamGame game) {
        this.game = game;
        batch = new SpriteBatch();
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        titleFont = CjkFonts.get(40);
        font = CjkFonts.get(20);
    }

    @Override
    public void show() {
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        saves = game.listSaves();
    }

    @Override
    public void resize(int width, int height) {
        cam.setToOrtho(false, width, height);
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.showTitle();
            return;
        }
        float w = cam.viewportWidth, h = cam.viewportHeight;
        float rowH = 64f, gap = 12f, panelW = Math.min(680f, w * 0.7f);
        float px = w / 2f - panelW / 2f;
        float rowsTop = h * 0.72f;
        int n = saves.size();

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            float mx = Gdx.input.getX(), my = h - Gdx.input.getY();
            for (int i = 0; i < n; i++) {
                float y = rowsTop - (i + 1) * (rowH + gap);
                if (mx >= px && mx <= px + panelW && my >= y && my <= y + rowH) {
                    game.continueGame(saves.get(i));
                    return;
                }
            }
            float by = rowsTop - (n + 1) * (rowH + gap) - 6f;
            if (mx >= px && mx <= px + panelW && my >= by && my <= by + rowH) {
                game.showTitle();
                return;
            }
        }
        hover = -1;
        float hx = Gdx.input.getX(), hy = h - Gdx.input.getY();
        for (int i = 0; i < n; i++) {
            float y = rowsTop - (i + 1) * (rowH + gap);
            if (hx >= px && hx <= px + panelW && hy >= y && hy <= y + rowH) {
                hover = i;
            }
        }

        Gdx.gl.glClearColor(0.05f, 0.04f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        titleFont.setColor(0.95f, 0.9f, 0.7f, 1f);
        center(titleFont, "选择要回到哪个梦", w / 2f, h - 70);
        titleFont.setColor(1, 1, 1, 1);

        if (n == 0) {
            font.setColor(0.7f, 0.72f, 0.85f, 1f);
            center(font, "还没有做过梦（先进“新的梦”，游戏内自动/手动存档）", w / 2f, h / 2f);
            font.setColor(1, 1, 1, 1);
        }
        for (int i = 0; i < n; i++) {
            float y = rowsTop - (i + 1) * (rowH + gap);
            WoodUi.plank(batch, pixel, px, y, panelW, rowH, i == hover);
            font.setColor(1, 1, 1, 1);
            font.draw(batch, summary(saves.get(i)), px + 20, y + rowH / 2f + 7f);
        }
        float by = rowsTop - (n + 1) * (rowH + gap) - 6f;
        WoodUi.plank(batch, pixel, px, by, panelW, rowH, hover == -2);
        font.setColor(1, 1, 1, 1);
        font.draw(batch, "返 回", px + 20, by + rowH / 2f + 7f);
        batch.end();
    }

    private static String summary(GameSave s) {
        String cn;
        try {
            cn = PlayerClass.valueOf(s.className).cn();
        } catch (Exception e) {
            cn = s.className;
        }
        int hh = (s.clockMinutes / 60) % 24, mm = s.clockMinutes % 60;
        return String.format("%s · 梦眠 %d/%d · 铸梦币 %d · 时刻 %02d:%02d",
                cn, s.lucidity, s.maxLucidity, s.coins, hh, mm);
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
