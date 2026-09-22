package com.cavedream.core.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.cavedream.core.CaveDreamGame;

import java.util.Random;

/**
 * 标题（登录）界面：星空 + 大月（呼应"梦眠=月相"的核心意象）+ 菜单。
 * 操作：↑/↓ 或 W/S 选择，Enter 确认，鼠标悬停+点击同效，ESC 退出游戏。
 * 注：暂用英文文案（libGDX 默认位图字体不含中文字形，中文字体 M3 随设置界面一起接入）。
 */
public class TitleScreen extends ScreenAdapter implements Disposable {

    private static final float W = 1280f;
    private static final float H = 720f;
    private static final String[] MENU = {"New Dream", "Continue", "Exit"};

    private final CaveDreamGame game;
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Viewport viewport;
    private Texture pixel;
    private Texture moon;
    private BitmapFont font;
    private float stateTime;

    // 星空：x, y, 相位, 闪烁速度
    private final float[] stars = new float[140 * 4];
    private int selected;
    private float noticeTime = -10f;
    private String notice = "";

    public TitleScreen(CaveDreamGame game) {
        this.game = game;
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        viewport = new FitViewport(W, H, camera);
        font = new BitmapFont();

        pixel = oneByOneWhite();
        moon = makeMoon(128);

        Random rnd = new Random(20260922L);
        for (int i = 0; i < stars.length; i += 4) {
            stars[i] = rnd.nextFloat() * W;           // x
            stars[i + 1] = rnd.nextFloat() * H;       // y
            stars[i + 2] = rnd.nextFloat() * 6.28f;   // phase
            stars[i + 3] = 0.6f + rnd.nextFloat() * 2f; // twinkle speed
        }
    }

    @Override
    public void show() {
        stateTime = 0;
        selected = 0;
    }

    @Override
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    @Override
    public void render(float delta) {
        stateTime += delta;

        handleInput();
        renderVisuals();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) {
            selected = (selected + MENU.length - 1) % MENU.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) {
            selected = (selected + 1) % MENU.length;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            confirm(selected);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            Gdx.app.exit();
        }

        // 鼠标悬停选择 + 点击确认
        float mx = Gdx.input.getX();
        float my = Gdx.input.getY();
        for (int i = 0; i < MENU.length; i++) {
            float ry = menuY(i);
            if (mx > W / 2 - 160 && mx < W / 2 + 160 && my > H - ry - 26 && my < H - ry + 34) {
                selected = i;
                if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
                    confirm(i);
                }
            }
        }
    }

    private void confirm(int index) {
        switch (index) {
            case 0 -> {
                Gdx.app.log("CaveDream", "坠入新的梦……");
                game.startNewDream();
            }
            case 1 -> showNotice("Not yet drifted off - no dream to continue (saves arrive in M3)");
            default -> Gdx.app.exit();
        }
    }

    private void showNotice(String text) {
        notice = text;
        noticeTime = stateTime;
    }

    private static float menuY(int i) {
        return 260 - i * 64;
    }

    private void renderVisuals() {
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        // 星空闪烁
        for (int i = 0; i < stars.length; i += 4) {
            float tw = 0.35f + 0.65f * Math.abs((float) Math.sin(stateTime * stars[i + 3] + stars[i + 2]));
            batch.setColor(0.8f, 0.85f, 1f, tw);
            batch.draw(pixel, stars[i], stars[i + 1], 2, 2);
        }
        batch.setColor(1, 1, 1, 1);

        // 大月：轻轻呼吸
        float pulse = 1f + 0.02f * (float) Math.sin(stateTime * 0.8f);
        float ms = 190 * pulse;
        batch.setColor(1f, 1f, 0.92f, 0.95f);
        batch.draw(moon, W - ms - 90, H - 210, ms, ms);
        batch.setColor(1, 1, 1, 1);

        // 标题
        font.getData().setScale(5.2f);
        font.setColor(0.92f, 0.88f, 1f, 1f);
        font.draw(batch, "C A V E D R E A M", W / 2 - 320, H - 170);
        font.getData().setScale(1.5f);
        font.setColor(0.55f, 0.6f, 0.85f, 1f);
        font.draw(batch, "- infinite dreams, each one a finite garden -", W / 2 - 235, H - 215);

        // 菜单
        font.getData().setScale(2f);
        for (int i = 0; i < MENU.length; i++) {
            boolean sel = i == selected;
            font.setColor(sel ? 1f : 0.45f, sel ? 0.95f : 0.48f, sel ? 0.6f : 0.6f, 1f);
            if (sel) {
                batch.setColor(1f, 0.9f, 0.5f, 0.9f);
                batch.draw(pixel, W / 2 - 150, menuY(i) - 20, 14, 14);
                batch.setColor(1, 1, 1, 1);
            }
            font.draw(batch, MENU[i], W / 2 - 120, menuY(i));
        }

        // 提示行 / 通知
        font.getData().setScale(1.2f);
        font.setColor(0.4f, 0.45f, 0.6f, 1f);
        font.draw(batch, "W/S or Arrows  ·  Enter  ·  Esc quits", 20, 30);
        font.draw(batch, "prototype v0.24  -  L1 Shallow Dream", W - 330, 30);
        float noticeAge = stateTime - noticeTime;
        if (noticeAge < 3f) {
            font.setColor(1f, 0.85f, 0.4f, Math.max(0, 1 - noticeAge / 3f));
            font.draw(batch, notice, W / 2 - 260, 120);
        }
        batch.end();
    }

    private static Texture oneByOneWhite() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    /** 程序化满月贴图：光晕 + 月盘 + 隐约月影斑点。 */
    private static Texture makeMoon(int size) {
        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setColor(0, 0, 0, 0);
        pm.fill();
        int c = size / 2;
        pm.setColor(1f, 0.98f, 0.86f, 0.12f);           // 外层光晕
        pm.fillCircle(c, c, c - 2);
        pm.setColor(1f, 0.97f, 0.82f, 0.25f);           // 内层光晕
        pm.fillCircle(c, c, (int) (c * 0.72f));
        pm.setColor(1f, 0.98f, 0.88f, 1f);              // 月盘
        pm.fillCircle(c, c, (int) (c * 0.58f));
        pm.setColor(0.93f, 0.9f, 0.78f, 1f);            // 月影斑
        pm.fillCircle(c - 14, c + 8, 8);
        pm.fillCircle(c + 10, c - 12, 5);
        pm.fillCircle(c + 4, c + 16, 4);
        Texture t = new Texture(pm);
        t.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pm.dispose();
        return t;
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        pixel.dispose();
        moon.dispose();
    }
}
