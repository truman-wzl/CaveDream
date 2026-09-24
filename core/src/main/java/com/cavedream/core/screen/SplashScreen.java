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
        font.getData().setScale(8f);      // 大字号，凸显品牌
    }

    @Override
    public void resize(int width, int height) {
        float a = width / (float) Math.max(1, height);
        cam.setToOrtho(false, 720f * a, 720f);   // 虚拟高 720 基准→字标随窗口等比放大
    }

    @Override
    public void show() {
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        t = 0f;
    }

    @Override
    public void render(float delta) {
        t += Math.min(delta, 1 / 30f);   // delta 设上限：预热字体等卡顿不会让计时跳帧、字标一闪而过
        if (t >= TOTAL || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)
                || Gdx.input.isKeyJustPressed(Input.Keys.ENTER)
                || Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            game.showTitle();
            return;
        }
        float alpha = t < FADE_IN ? t / FADE_IN
                : t < FADE_IN + HOLD ? 1f
                : Math.max(0f, 1f - (t - FADE_IN - HOLD) / FADE_OUT);
        // 淡入时字标轻微上浮（幅度小，保持居中观感）
        float rise = (1f - alpha) * 6f;

        Gdx.gl.glClearColor(0.01f, 0.012f, 0.03f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        String word = "CaveDream";
        measure.setText(font, word);
        float x = cam.viewportWidth / 2f - measure.width / 2f;
        float y = cam.viewportHeight / 2f - measure.height / 2f + rise;
        // 青色辉光（四周多遍叠）→ 凸显品牌
        font.setColor(new Color(0.4f, 0.7f, 1f, alpha * 0.4f));
        for (int o = 2; o <= 6; o += 2) {
            font.draw(batch, word, x + o, y);
            font.draw(batch, word, x - o, y);
            font.draw(batch, word, x, y + o);
            font.draw(batch, word, x, y - o);
        }
        // 纯白主体
        font.setColor(new Color(1f, 1f, 1f, alpha));
        font.draw(batch, word, x, y);
        batch.end();
        font.setColor(Color.WHITE);
        if (warm < WARM_SIZES.length) {
            CjkFonts.get(WARM_SIZES[warm++]);   // 画完再预热下一套字体（本帧已显示字标，卡顿不影响观感）
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}
