package com.cavedream.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.CaveDreamGame;

/**
 * 开场 Splash：黑底居中「CaveDream」字标淡入 → 停留 → 淡出，随后顺滑切到主菜单（TitleScreen）。
 * 任意按键/点击可跳过。纯程序化，无外部素材。
 */
public class SplashScreen extends ScreenAdapter implements Disposable {

    private static final float FADE_IN = 1.2f;
    private static final float HOLD = 1.6f;
    private static final float FADE_OUT = 1.0f;
    private static final float TOTAL = FADE_IN + HOLD + FADE_OUT;

    // 开场黑底期间逐帧预热各屏中文字体（把建字库的卡顿藏在开场，避免主菜单首帧黑屏）
    private static final int[] WARM_SIZES = {20, 26, 40, 48, 15};
    private int warm;

    private final CaveDreamGame game;
    private final OrthographicCamera cam = new OrthographicCamera();
    private SpriteBatch batch;
    private BitmapFont font;
    private final GlyphLayout measure = new GlyphLayout();
    private float t;

    public SplashScreen(CaveDreamGame game) {
        this.game = game;
        batch = new SpriteBatch();
        font = new BitmapFont();          // 默认字体含 ASCII，够画 "CaveDream"
        font.getData().setScale(4.2f);
    }

    @Override
    public void resize(int width, int height) {
        cam.setToOrtho(false, width, height);
    }

    @Override
    public void show() {
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        t = 0f;
    }

    @Override
    public void render(float delta) {
        if (warm < WARM_SIZES.length) {
            CjkFonts.get(WARM_SIZES[warm++]);   // 每帧预热一套字体
        }
        t += delta;
        if (t >= TOTAL || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            game.showTitle();
            return;
        }
        float alpha = t < FADE_IN ? t / FADE_IN
                : t < FADE_IN + HOLD ? 1f
                : Math.max(0f, 1f - (t - FADE_IN - HOLD) / FADE_OUT);
        // 淡入时字标轻微上浮，更顺滑
        float rise = (1f - alpha) * 14f;

        Gdx.gl.glClearColor(0.01f, 0.012f, 0.03f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        String word = "CaveDream";
        measure.setText(font, word);
        float x = cam.viewportWidth / 2f - measure.width / 2f;
        float y = cam.viewportHeight / 2f - measure.height / 2f + rise;
        font.setColor(new Color(0.92f, 0.88f, 1f, alpha));
        font.draw(batch, word, x, y);
        batch.end();
        font.setColor(Color.WHITE);
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
