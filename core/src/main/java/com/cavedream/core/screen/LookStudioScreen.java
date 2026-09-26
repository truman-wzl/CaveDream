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
import com.cavedream.core.anim.BoneId;
import com.cavedream.core.anim.Pose;
import com.cavedream.core.appearance.LookDice;
import com.cavedream.core.appearance.LookSpec;
import com.cavedream.core.appearance.PixelLookForge;
import com.cavedream.core.player.PlayerClass;
import com.cavedream.core.render.SkeletalAvatar;
import com.cavedream.core.render.WoodUi;

/**
 * 外观骰子屏（取代角色捏脸画板）：投骰从受控参数空间取一套 {@link LookSpec}，用 {@link SkeletalAvatar}
 * 以待机姿态实时预览锻造出的骨骼小人；可按槽（体型/配色/发型/配饰）锁定后局部重掷；确认→生成世界。
 * 运行时纯离线确定性（无 AI/网络）。
 */
public class LookStudioScreen extends ScreenAdapter implements Disposable {

    private static final String[] HAIR_NAMES = {"短发", "长发", "双马尾", "刺发", "光头"};
    private static final String[] PALETTE_NAMES = {
            "通灵·紫", "寒星·蓝", "熔梦·赤", "林语·翠", "流金·琥", "霜铁·灰", "樱醉·粉", "梦水·青"};
    private static final String[] LOCK_NAMES = {"体型", "配色", "发型", "配饰"};
    private static final int[] LOCK_BITS = {LookDice.BODY, LookDice.PALETTE, LookDice.HAIR, LookDice.EXTRAS};

    private final CaveDreamGame game;
    private final PlayerClass playerClass;
    private final String saveName;
    private LookSpec spec;
    private int locked;                       // 锁定槽掩码（位=1 表示重掷时保留）

    private final OrthographicCamera cam = new OrthographicCamera();
    private SpriteBatch batch;
    private Texture pixel;
    private BitmapFont titleFont;
    private BitmapFont font;
    private BitmapFont smallFont;
    private final GlyphLayout layout = new GlyphLayout();

    private SkeletalAvatar avatar;
    private final Pose previewPose = new Pose();
    private float time;
    private float showT;

    // 布局缓存
    private float prevCx, prevCy, prevH;
    private float btnX, btnW, btnH, diceY, lockY0, lockGap, confirmY;

    public LookStudioScreen(CaveDreamGame game, PlayerClass playerClass, String saveName) {
        this.game = game;
        this.playerClass = playerClass;
        this.saveName = saveName == null ? "" : saveName;
        this.spec = LookDice.roll(new java.util.Random().nextLong());
        batch = new SpriteBatch();
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
        titleFont = CjkFonts.get(34);
        font = CjkFonts.get(18);
        smallFont = CjkFonts.get(13);
        avatar = new SkeletalAvatar();
        rebuildAvatar();
    }

    private void rebuildAvatar() {
        avatar.setAppearance(PixelLookForge.forge(spec));
    }

    private void reroll() {
        spec = LookDice.reroll(spec, new java.util.Random().nextLong(), locked);
        rebuildAvatar();
    }

    @Override
    public void resize(int width, int height) {
        cam.setToOrtho(false, width, height);
        float w = width, h = height;
        prevH = h * 0.62f;
        prevCx = w * 0.32f;
        prevCy = h * 0.5f;
        btnW = Math.min(w * 0.22f, 300f);
        btnH = h * 0.07f;
        btnX = w * 0.62f;
        diceY = h * 0.66f;
        lockY0 = h * 0.52f;
        lockGap = btnH * 1.25f;
        confirmY = h * 0.16f;
    }

    @Override
    public void show() {
        showT = 0f;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void render(float delta) {
        showT += delta;
        time += delta;
        boolean ready = showT > 0.25f;
        if (ready) {
            handleInput();
        }
        Gdx.gl.glClearColor(0.05f, 0.04f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        draw();
        batch.end();
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.showTitle();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) || Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            reroll();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            confirm();
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        float mx = Gdx.input.getX();
        float my = cam.viewportHeight - Gdx.input.getY();
        if (inRect(mx, my, btnX, diceY, btnW, btnH)) {
            reroll();
            return;
        }
        for (int i = 0; i < 4; i++) {
            float y = lockY0 - i * lockGap;
            if (inRect(mx, my, btnX, y, btnW, btnH)) {
                locked ^= LOCK_BITS[i];
                return;
            }
        }
        if (inRect(mx, my, btnX, confirmY, btnW, btnH * 1.2f)) {
            confirm();
        }
    }

    private void confirm() {
        game.startNewDream(playerClass, saveName, spec);
    }

    private void draw() {
        float w = cam.viewportWidth, h = cam.viewportHeight;
        titleFont.setColor(0.95f, 0.9f, 0.72f, 1f);
        center(titleFont, "掷出你的梦之躯", w / 2f, h - 46);
        titleFont.setColor(1, 1, 1, 1);

        // 预览框
        float boxW = prevH * 0.72f, boxH = prevH * 1.12f;
        WoodUi.panel(batch, pixel, prevCx - boxW / 2f, prevCy - boxH / 2f, boxW, boxH);
        float sw = prevH / 2f, sh = prevH;      // 精灵世界尺寸（32:64 比例）
        previewPose.reset();
        float breath = (float) Math.sin(time * 2f);
        previewPose.setBone(BoneId.SPINE, 0, 0, breath * 0.9f, 1, 1);   // 与游戏内待机同一套刚性呼吸
        previewPose.setBone(BoneId.HEAD, 0, 0, breath * 0.6f, 1, 1);
        previewPose.setBone(BoneId.ARM_FB, -3f + breath * 1.5f, 0, 0, 1, 1);
        previewPose.setBone(BoneId.ARM_UB, 3f - breath * 1.5f, 0, 0, 1, 1);
        avatar.applyPose(previewPose);
        avatar.draw(batch, prevCx - sw / 2f, prevCy - sh / 2f, sw, sh, 1, 0f);

        // 当前蓝图摘要
        font.setColor(0.85f, 0.88f, 1f, 1f);
        String summary = "发型 " + HAIR_NAMES[clampIdx(spec.hairstyle, HAIR_NAMES.length)]
                + "　配色 " + PALETTE_NAMES[Math.floorMod(spec.paletteIndex, PALETTE_NAMES.length)];
        center(font, summary, prevCx, prevCy - boxH / 2f - 24);
        font.setColor(1, 1, 1, 1);

        // 按钮
        drawButton(btnX, diceY, btnW, btnH, "投骰子（重掷）", true);
        for (int i = 0; i < 4; i++) {
            boolean on = (locked & LOCK_BITS[i]) != 0;
            drawButton(btnX, lockY0 - i * lockGap, btnW, btnH, (on ? "锁 · " : "自 · ") + LOCK_NAMES[i], on);
        }
        drawButton(btnX, confirmY, btnW, btnH * 1.2f, "确认 · 入梦", true);

        smallFont.setColor(0.6f, 0.62f, 0.75f, 1f);
        center(smallFont, "空格/点击=重掷　锁定后仅重掷未锁槽　Enter 确认　Esc 返回", w / 2f, 30);
        smallFont.setColor(1, 1, 1, 1);
    }

    private void drawButton(float x, float y, float bw, float bh, String label, boolean hot) {
        WoodUi.plank(batch, pixel, x, y, bw, bh, hot);
        font.setColor(hot ? Color.WHITE : new Color(0.8f, 0.75f, 0.6f, 1f));
        center(font, label, x + bw / 2f, y + bh * 0.4f);
        font.setColor(1, 1, 1, 1);
    }

    private static int clampIdx(byte v, int len) {
        int i = v;
        return i < 0 || i >= len ? 0 : i;
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
        if (avatar != null) {
            avatar.dispose();
        }
    }
}
