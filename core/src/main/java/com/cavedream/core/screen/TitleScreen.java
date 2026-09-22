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
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.cavedream.core.CaveDreamGame;
import com.cavedream.core.net.CloudAuthClient;

import java.util.Random;

/**
 * 标题（登录）界面：星空 + 大月 + 主菜单 + 账号面板（注册/登录/找回密码）。
 * 账号操作走 CloudAuthClient 异步请求（不阻塞渲染线程），会话存于外部文件 cavedream/session.json。
 * 注：暂用英文文案（默认位图字体无中文字形，中文字体 M3 随设置界面接入）。
 */
public class TitleScreen extends ScreenAdapter implements Disposable {

    private static final float W = 1280f;
    private static final float H = 720f;
    private static final String SERVER = "http://localhost:8081";
    private static final String[] MENU = {"新的梦", "继续游戏", "账号", "退出游戏"};

    private enum Mode { LOGIN, REGISTER, RESET }

    private final CaveDreamGame game;
    private final CloudAuthClient auth = new CloudAuthClient(SERVER);

    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Texture pixel;
    private Texture moon;
    private BitmapFont titleFont;
    private BitmapFont menuFont;
    private BitmapFont uiFont;
    private float stateTime;

    private final float[] stars = new float[140 * 4];
    private final Vector3 screenPos = new Vector3();
    private final GlyphLayout measure = new GlyphLayout();
    private int selected;
    private float noticeTime = -10f;
    private String notice = "";
    private String signedInAs = null;

    /* ---- 账号面板（Scene2D） ---- */
    private Stage stage;
    private Table panel;
    private boolean accountOpen;
    private Mode mode = Mode.LOGIN;
    private TextField tfEmail, tfPass, tfNick, tfCode;
    private Table rowCode, rowNick;
    private TextButton btnSendCode;
    private TextButton btnLogin, btnRegister, btnReset, btnSubmit, btnClose;
    private Label statusLabel;
    private volatile CloudAuthClient.Result pendingResult;
    private boolean busy;

    public TitleScreen(CaveDreamGame game) {
        this.game = game;
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        titleFont = CjkFonts.create(58, " 梦迹行者");   // 大字标题只用小字集，省显存
        menuFont = CjkFonts.create(26);
        uiFont = CjkFonts.create(20);

        pixel = oneByOneWhite();
        moon = makeMoon(128);
        buildAccountPanel();

        Random rnd = new Random(20260922L);
        for (int i = 0; i < stars.length; i += 4) {
            stars[i] = rnd.nextFloat() * W;
            stars[i + 1] = rnd.nextFloat() * H;
            stars[i + 2] = rnd.nextFloat() * 6.28f;
            stars[i + 3] = 0.6f + rnd.nextFloat() * 2f;
        }
        loadSession();
    }

    @Override
    public void show() {
        stateTime = 0;
        selected = 0;
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, false);
    }

    @Override
    public void render(float delta) {
        stateTime += delta;
        consumePendingResult();
        handleInput();
        renderVisuals();
        if (accountOpen) {
            stage.act(delta);
            stage.draw();
        }
    }

    /* ---------------- 输入 ---------------- */

    private void handleInput() {
        if (accountOpen) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
                closeAccount();
            }
            return; // 面板打开时主菜单不响应，输入由 Stage 接管
        }
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
        // 鼠标悬停选择 + 点击确认：先把屏幕像素换算回 1280×720 逻辑坐标（全屏/多分辨率下必需）
        screenPos.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(screenPos);
        float mx = screenPos.x;
        float my = screenPos.y;
        for (int i = 0; i < MENU.length; i++) {
            float ry = menuY(i);
            if (mx > W / 2 - 160 && mx < W / 2 + 160 && my > ry - 26 && my < ry + 34) {
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
            case 1 -> showNotice("还没有做过梦，无法继续（存档系统随 M3 上线）");
            case 2 -> openAccount();
            default -> Gdx.app.exit();
        }
    }

    private static float menuY(int i) {
        return 260 - i * 64;
    }

    private void showNotice(String text) {
        notice = text;
        noticeTime = stateTime;
    }

    /* ---------------- 账号面板 ---------------- */

    private void buildAccountPanel() {
        stage = new Stage(new StretchViewport(W, H, camera));
        Skin skin = new Skin();
        skin.add("white", new TextureRegion(pixel));

        Drawable fieldBg = new TextureRegionDrawable(skin.getRegion("white"))
                .tint(new Color(0.10f, 0.12f, 0.20f, 0.95f));
        TextField.TextFieldStyle tfStyle = new TextField.TextFieldStyle();
        tfStyle.font = uiFont;
        tfStyle.fontColor = Color.WHITE;
        tfStyle.background = fieldBg;
        tfStyle.cursor = new TextureRegionDrawable(skin.getRegion("white")).tint(Color.YELLOW);
        tfStyle.selection = new TextureRegionDrawable(skin.getRegion("white")).tint(new Color(0.3f, 0.4f, 0.8f, 0.6f));

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = uiFont;
        btnStyle.fontColor = new Color(0.9f, 0.9f, 1f, 1f);
        btnStyle.up = new TextureRegionDrawable(skin.getRegion("white")).tint(new Color(0.18f, 0.2f, 0.34f, 1f));
        btnStyle.down = new TextureRegionDrawable(skin.getRegion("white")).tint(new Color(0.32f, 0.36f, 0.6f, 1f));
        btnStyle.checked = new TextureRegionDrawable(skin.getRegion("white")).tint(new Color(0.5f, 0.42f, 0.16f, 1f));

        Label.LabelStyle labelStyle = new Label.LabelStyle(uiFont, new Color(0.65f, 0.7f, 0.9f, 1f));

        tfEmail = new TextField("", tfStyle);
        tfPass = new TextField("", tfStyle);
        tfPass.setPasswordCharacter('*');
        tfNick = new TextField("", tfStyle);
        tfCode = new TextField("", tfStyle);

        btnLogin = new TextButton("登录", btnStyle);
        btnRegister = new TextButton("注册", btnStyle);
        btnReset = new TextButton("找回密码", btnStyle);
        btnSubmit = new TextButton("登录", btnStyle);
        btnSendCode = new TextButton("发送验证码", btnStyle);
        btnClose = new TextButton("关闭", btnStyle);
        statusLabel = new Label("欢迎入梦。", labelStyle);

        btnLogin.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { setMode(Mode.LOGIN); } });
        btnRegister.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { setMode(Mode.REGISTER); } });
        btnReset.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { setMode(Mode.RESET); } });
        btnSubmit.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { submit(); } });
        btnSendCode.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { sendCode(); } });
        btnClose.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { closeAccount(); } });

        Drawable panelBg = new TextureRegionDrawable(skin.getRegion("white"))
                .tint(new Color(0.06f, 0.07f, 0.13f, 0.96f));
        panel = new Table();
        panel.setBackground(panelBg);
        panel.pad(18);
        panel.defaults().spaceBottom(8);

        Table modes = new Table();
        modes.defaults().spaceRight(8);
        modes.add(btnLogin);
        modes.add(btnRegister);
        modes.add(btnReset);

        Label titleLabel = new Label("账号中心", labelStyle);
        titleLabel.setFontScale(1.6f);
        panel.add(titleLabel).row();
        panel.add(modes).row();
        panel.add(row("邮箱", tfEmail)).width(460).row();
        rowCode = new Table();
        rowCode.defaults().spaceRight(8);
        rowCode.add(btnSendCode);
        Label.LabelStyle codeLs = new Label.LabelStyle(uiFont, new Color(0.55f, 0.6f, 0.8f, 1f));
        rowCode.add(new Label("验证码", codeLs)).width(64);
        rowCode.add(tfCode).expandX().fillX().height(34);
        rowNick = row("昵称", tfNick);
        panel.add(row("密码", tfPass)).width(460).row();
        panel.add(rowNick).width(460).row();
        panel.add(rowCode).width(460).row();
        Table actions = new Table();
        actions.defaults().spaceRight(8);
        actions.add(btnSubmit);
        actions.add(btnClose);
        panel.add(actions).left().row();
        panel.add(statusLabel).left().width(420).row();

        panel.pack();
        panel.setPosition((W - panel.getWidth()) / 2f, (H - panel.getHeight()) / 2f);
        stage.addActor(panel);
        setMode(Mode.LOGIN);
    }

    private Table row(String caption, TextField field) {
        Table t = new Table();
        t.defaults().spaceRight(8);
        Label.LabelStyle ls = new Label.LabelStyle(uiFont, new Color(0.55f, 0.6f, 0.8f, 1f));
        t.add(new Label(caption, ls)).width(64);
        t.add(field).expandX().fillX().height(34);
        return t;
    }

    private void setMode(Mode m) {
        mode = m;
        rowNick.setVisible(m == Mode.REGISTER);
        rowCode.setVisible(m != Mode.LOGIN);
        btnLogin.setChecked(m == Mode.LOGIN);
        btnRegister.setChecked(m == Mode.REGISTER);
        btnReset.setChecked(m == Mode.RESET);
        btnSubmit.setText(switch (m) {
            case LOGIN -> "登录";
            case REGISTER -> "注册";
            case RESET -> "重置密码";
        });
        panel.invalidateHierarchy();
    }

    private void openAccount() {
        accountOpen = true;
        statusLabel.setText(signedInAs == null ? "欢迎入梦。" : "已登录：" + signedInAs);
        Gdx.input.setInputProcessor(stage);
    }

    private void closeAccount() {
        accountOpen = false;
        Gdx.input.setInputProcessor(null);
    }

    /** 提交：异步请求，避免网络阻塞渲染线程。 */
    private void submit() {
        if (busy) {
            return;
        }
        String email = tfEmail.getText().trim();
        String pass = tfPass.getText();
        if (email.isEmpty() || pass.isEmpty()) {
            statusLabel.setText("邮箱和密码不能为空");
            return;
        }
        busy = true;
        statusLabel.setText("连接中……");
        Mode m = mode;
        String nick = tfNick.getText().trim();
        String code = tfCode.getText().trim();
        new Thread(() -> {
            CloudAuthClient.Result r = switch (m) {
                case LOGIN -> auth.login(email, pass);
                case REGISTER -> auth.register(email, code, pass, nick);
                case RESET -> auth.resetPassword(email, code, pass);
            };
            pendingResult = r;
        }, "cavedream-auth").start();
    }

    /** 发送邮箱验证码（注册/找回模式）。 */
    private void sendCode() {
        if (busy) {
            return;
        }
        String email = tfEmail.getText().trim();
        if (email.isEmpty()) {
            statusLabel.setText("请先填写邮箱");
            return;
        }
        busy = true;
        statusLabel.setText("验证码发送中……");
        new Thread(() -> pendingResult = auth.sendCode(email), "cavedream-sendcode").start();
    }

    /** 主线程消费异步结果。 */
    private void consumePendingResult() {
        CloudAuthClient.Result r = pendingResult;
        if (r == null) {
            return;
        }
        pendingResult = null;
        busy = false;
        statusLabel.setText(r.message());
        if (r.ok() && r.token() != null) {
            signedInAs = r.nickname() == null ? tfEmail.getText().trim() : r.nickname();
            saveSession(r.token(), signedInAs);
        }
    }

    /* ---------------- 会话持久化 ---------------- */

    private void saveSession(String token, String nickname) {
        Gdx.files.external("cavedream/session.json")
                .writeString("{\"token\":\"" + token + "\",\"nickname\":\"" + nickname + "\"}", false);
    }

    private void loadSession() {
        try {
            com.badlogic.gdx.files.FileHandle fh = Gdx.files.external("cavedream/session.json");
            if (fh.exists()) {
                String body = fh.readString();
                int a = body.indexOf("\"nickname\":\"");
                int b = body.indexOf('"', a + 12);
                if (a >= 0 && b > a) {
                    signedInAs = body.substring(a + 12, b);
                }
            }
        } catch (Exception ignore) {
            // 会话文件损坏当作未登录
        }
    }

    /* ---------------- 渲染 ---------------- */

    private void renderVisuals() {
        Gdx.gl.glClearColor(0.02f, 0.03f, 0.08f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();

        for (int i = 0; i < stars.length; i += 4) {
            float tw = 0.35f + 0.65f * Math.abs((float) Math.sin(stateTime * stars[i + 3] + stars[i + 2]));
            batch.setColor(0.8f, 0.85f, 1f, tw);
            batch.draw(pixel, stars[i], stars[i + 1], 2, 2);
        }
        batch.setColor(1, 1, 1, 1);

        float pulse = 1f + 0.02f * (float) Math.sin(stateTime * 0.8f);
        float ms = 190 * pulse;
        batch.setColor(1f, 1f, 0.92f, 0.95f);
        batch.draw(moon, W - ms - 90, H - 210, ms, ms);
        batch.setColor(1, 1, 1, 1);

        titleFont.setColor(0.92f, 0.88f, 1f, 1f);
        drawCentered(batch, titleFont, "梦 迹 行 者", H - 130);
        menuFont.setColor(0.55f, 0.6f, 0.85f, 1f);
        drawCentered(batch, menuFont, "—— 无限的是梦，有限的是每个梦 ——", H - 200);
        menuFont.setColor(1, 1, 1, 1);

        for (int i = 0; i < MENU.length; i++) {
            boolean sel = i == selected && !accountOpen;
            menuFont.setColor(sel ? 1f : 0.45f, sel ? 0.95f : 0.48f, sel ? 0.6f : 0.6f, 1f);
            float mw = textWidth(menuFont, MENU[i]);
            float mx = W / 2 - mw / 2;
            if (sel) {
                batch.setColor(1f, 0.9f, 0.5f, 0.9f);
                batch.draw(pixel, mx - 30, menuY(i) - 20, 14, 14);
                batch.setColor(1, 1, 1, 1);
            }
            menuFont.draw(batch, MENU[i], mx, menuY(i));
        }
        if (signedInAs != null) {
            uiFont.setColor(0.6f, 0.9f, 0.7f, 1f);
            drawCentered(batch, uiFont, "已登录：" + signedInAs, menuY(3) - 70);
            uiFont.setColor(1, 1, 1, 1);
        }

        uiFont.setColor(0.4f, 0.45f, 0.6f, 1f);
        uiFont.draw(batch, "W/S 或方向键选择 · 回车确认 · F11 全屏/窗口 · ESC 退出", 20, 36);
        uiFont.draw(batch, "梦迹行者 v0.35 · 服务端 " + SERVER, W - 460, 36);
        float noticeAge = stateTime - noticeTime;
        if (noticeAge < 3f) {
            uiFont.setColor(1f, 0.85f, 0.4f, Math.max(0, 1 - noticeAge / 3f));
            drawCentered(batch, uiFont, notice, 130);
        }
        uiFont.setColor(1, 1, 1, 1);
        batch.end();
    }

    /** 水平居中绘制一行文字。 */
    private void drawCentered(SpriteBatch b, BitmapFont f, String text, float baselineY) {
        f.draw(b, text, W / 2 - textWidth(f, text) / 2, baselineY);
    }

    /** 用 GlyphLayout 量文字宽度（getBounds 是 protected）。 */
    private float textWidth(BitmapFont f, String text) {
        measure.setText(f, text);
        return measure.width;
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
        pm.setColor(1f, 0.98f, 0.86f, 0.12f);
        pm.fillCircle(c, c, c - 2);
        pm.setColor(1f, 0.97f, 0.82f, 0.25f);
        pm.fillCircle(c, c, (int) (c * 0.72f));
        pm.setColor(1f, 0.98f, 0.88f, 1f);
        pm.fillCircle(c, c, (int) (c * 0.58f));
        pm.setColor(0.93f, 0.9f, 0.78f, 1f);
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
        stage.dispose();
        batch.dispose();
        titleFont.dispose();
        menuFont.dispose();
        uiFont.dispose();
        pixel.dispose();
        moon.dispose();
    }
}
