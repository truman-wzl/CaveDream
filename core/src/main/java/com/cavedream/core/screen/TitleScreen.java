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
import com.cavedream.core.render.WoodUi;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
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

    private enum Mode { LOGIN, REGISTER, RESET, USER }

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
    private float fadeIn;

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
    private TextField tfEmail, tfPass, tfNick, tfCode, tfUser;
    private Table rowCode, rowNick, rowUser, rowEmail, rowPass, modes;
    private Label titleLabel;
    private Label emailCaption;
    private TextButton btnSendCode;
    private TextButton btnLogin, btnRegister, btnReset, btnSubmit, btnClose, btnLogout;
    private Label statusLabel;
    private String sessionToken;   // 登录成功后持有，供改昵称/登出
    private boolean loggingOut;
    private volatile boolean sessionInvalid;   // 启动校验发现 token 失效→清成未登录
    private volatile CloudAuthClient.Result pendingResult;
    private boolean busy;

    public TitleScreen(CaveDreamGame game) {
        this.game = game;
        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        titleFont = CjkFonts.create(58, " 梦迹行者");   // 大字标题只用小字集，省显存
        menuFont = CjkFonts.get(26);
        uiFont = CjkFonts.get(20);

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
        fadeIn = 0f;
        validateSession();
    }

    /** 启动校验：本地存了登录态则后台问服务端 token 是否仍有效，失效则清。 */
    private void validateSession() {
        if (signedInAs == null) {
            return;
        }
        final String tok = game.sessionToken();
        if (tok == null || tok.isBlank()) {
            signedInAs = null;
            return;
        }
        new Thread(() -> {
            CloudAuthClient.Result r = auth.me(tok);
            if (!r.ok()) {
                sessionInvalid = true;
            }
        }, "cavedream-validate").start();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, false);
    }

    @Override
    public void render(float delta) {
        if (sessionInvalid) {
            sessionInvalid = false;
            signedInAs = null;
            game.setSession(null, null);
            if (accountOpen) {
                setMode(Mode.LOGIN);
            }
        }
        stateTime += delta;
        fadeIn = Math.min(1f, fadeIn + delta / 0.6f);   // 黑屏→主菜单 0.6s 淡入
        consumePendingResult();
        String cloudMsg = game.takeCloudMsg();   // 回显上次“保存并退出”的云端同步结果
        if (cloudMsg != null) {
            showNotice(cloudMsg);
        }
        handleInput();
        renderVisuals();
        if (accountOpen) {
            stage.act(delta);
            stage.draw();
        }
        if (fadeIn < 1f) {                                // 顶部盖一层渐隐黑幕，与 splash 衔接
            batch.setProjectionMatrix(camera.combined);
            batch.begin();
            batch.setColor(0, 0, 0, 1f - fadeIn);
            batch.draw(pixel, 0, 0, W, H);
            batch.setColor(1, 1, 1, 1);
            batch.end();
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
            if (mx > W / 2 - 150 && mx < W / 2 + 150 && my > ry - 38 && my < ry + 14) {
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
            case 1 -> game.showSaveList();
            case 2 -> openAccount();
            default -> Gdx.app.exit();
        }
    }

    private static float menuY(int i) {
        return 360 - i * 64;   // 整体上移、竖向偏居中，与大标题/副标题保持间距
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
        TextureRegionDrawable white = new TextureRegionDrawable(skin.getRegion("white"));

        // 输入框：深木凹槽
        Drawable fieldBg = white.tint(new Color(0.14f, 0.10f, 0.06f, 0.96f));
        TextField.TextFieldStyle tfStyle = new TextField.TextFieldStyle();
        tfStyle.font = uiFont;
        tfStyle.fontColor = new Color(0.96f, 0.92f, 0.82f, 1f);
        tfStyle.background = fieldBg;
        tfStyle.cursor = white.tint(Color.YELLOW);
        tfStyle.selection = white.tint(new Color(0.5f, 0.38f, 0.16f, 0.6f));

        // 木按钮：常态木色 / 悬停浮亮 / 按下压暗 / 选中描金
        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = uiFont;
        btnStyle.fontColor = new Color(0.98f, 0.93f, 0.8f, 1f);
        btnStyle.up = white.tint(new Color(0.42f, 0.28f, 0.15f, 1f));
        btnStyle.over = white.tint(new Color(0.60f, 0.42f, 0.21f, 1f));
        btnStyle.down = white.tint(new Color(0.30f, 0.19f, 0.10f, 1f));
        btnStyle.checked = white.tint(new Color(0.74f, 0.55f, 0.22f, 1f));

        Label.LabelStyle labelStyle = new Label.LabelStyle(uiFont, new Color(0.86f, 0.74f, 0.5f, 1f));

        tfEmail = new TextField("", tfStyle);
        tfPass = new TextField("", tfStyle);
        tfPass.setPasswordCharacter('*');
        tfUser = new TextField("", tfStyle);
        tfNick = new TextField("", tfStyle);
        tfCode = new TextField("", tfStyle);

        btnLogin = new TextButton("登录", btnStyle);
        btnRegister = new TextButton("注册", btnStyle);
        btnReset = new TextButton("找回密码", btnStyle);
        btnSubmit = new TextButton("登录", btnStyle);
        btnSendCode = new TextButton("发送验证码", btnStyle);
        btnLogout = new TextButton("登出", btnStyle);
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
        btnLogout.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { doLogout(); } });
        btnClose.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { closeAccount(); } });

        Drawable panelBg = white.tint(new Color(0.26f, 0.17f, 0.09f, 0.97f));   // 木板底
        panel = new Table();
        panel.setBackground(panelBg);
        panel.pad(20);
        panel.defaults().spaceBottom(8);

        modes = new Table();
        modes.defaults().spaceRight(8);
        modes.add(btnLogin);
        modes.add(btnRegister);
        modes.add(btnReset);

        titleLabel = new Label("账号中心", labelStyle);
        titleLabel.setFontScale(1.6f);
        panel.add(titleLabel).row();
        panel.add(modes).row();
        rowEmail = new Table();
        rowEmail.defaults().spaceRight(8);
        emailCaption = new Label("账号(用户名/邮箱)", labelStyle);
        rowEmail.add(emailCaption).width(130);
        rowEmail.add(tfEmail).expandX().fillX().height(34);
        panel.add(rowEmail).width(500).row();
        rowUser = row("用户名", tfUser);
        rowCode = new Table();
        rowCode.defaults().spaceRight(8);
        rowCode.add(btnSendCode);
        Label.LabelStyle codeLs = new Label.LabelStyle(uiFont, new Color(0.7f, 0.6f, 0.42f, 1f));
        rowCode.add(new Label("验证码", codeLs)).width(64);
        rowCode.add(tfCode).expandX().fillX().height(34);
        rowNick = row("昵称", tfNick);
        panel.add(rowUser).width(500).row();
        rowPass = row("密码", tfPass);
        panel.add(rowPass).width(500).row();
        panel.add(rowNick).width(500).row();
        panel.add(rowCode).width(500).row();
        Table actions = new Table();
        actions.defaults().spaceRight(8);
        actions.add(btnSubmit);
        actions.add(btnLogout);
        actions.add(btnClose);
        panel.add(actions).left().row();
        panel.add(statusLabel).left().width(440).row();

        panel.pack();
        panel.setPosition((W - panel.getWidth()) / 2f, (H - panel.getHeight()) / 2f);
        stage.addActor(panel);
        setMode(Mode.LOGIN);
    }

    /** 悬停反馈：靠 over 木色高亮（不用位移，避免按钮参差）。 */
    private Table row(String caption, TextField field) {
        Table t = new Table();
        t.defaults().spaceRight(8);
        Label.LabelStyle ls = new Label.LabelStyle(uiFont, new Color(0.86f, 0.74f, 0.5f, 1f));
        t.add(new Label(caption, ls)).width(130);
        t.add(field).expandX().fillX().height(34);
        return t;
    }

    private void setMode(Mode m) {
        mode = m;
        boolean user = m == Mode.USER;
        titleLabel.setText(user ? "用户中心" : "账号中心");
        emailCaption.setText(m == Mode.LOGIN ? "账号(用户名/邮箱)" : "邮箱");
        modes.setVisible(!user);
        rowEmail.setVisible(!user);
        rowUser.setVisible(m == Mode.REGISTER);
        rowPass.setVisible(!user);
        rowNick.setVisible(m == Mode.REGISTER || user);
        rowCode.setVisible(m == Mode.REGISTER || m == Mode.RESET);
        btnLogout.setVisible(user);
        btnLogin.setChecked(m == Mode.LOGIN);
        btnRegister.setChecked(m == Mode.REGISTER);
        btnReset.setChecked(m == Mode.RESET);
        btnSubmit.setText(switch (m) {
            case LOGIN -> "登录";
            case REGISTER -> "注册";
            case RESET -> "重置密码";
            case USER -> "保存昵称";
        });
        if (user) {
            tfNick.setText(signedInAs == null ? "" : signedInAs);
            statusLabel.setText("当前登录：" + signedInAs);
        }
        panel.invalidateHierarchy();
    }

    private void openAccount() {
        accountOpen = true;
        Gdx.input.setInputProcessor(stage);
        setMode(signedInAs != null ? Mode.USER : Mode.LOGIN);
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
        if (mode == Mode.USER) {
            saveNickname();
            return;
        }
        String account = tfEmail.getText().trim();
        String pass = tfPass.getText();
        if (account.isEmpty() || pass.isEmpty()) {
            statusLabel.setText("账号和密码不能为空");
            return;
        }
        busy = true;
        statusLabel.setText("连接中……");
        Mode m = mode;
        String user = tfUser.getText().trim();
        String nick = tfNick.getText().trim();
        String code = tfCode.getText().trim();
        new Thread(() -> {
            CloudAuthClient.Result r = switch (m) {
                case LOGIN -> auth.login(account, pass);
                case REGISTER -> auth.register(account, user, code, pass, nick);
                case RESET -> auth.resetPassword(account, code, pass);
                default -> null;
            };
            pendingResult = r;
        }, "cavedream-auth").start();
    }

    /** 用户中心：保存新昵称。 */
    private void saveNickname() {
        if (busy) {
            return;
        }
        String nick = tfNick.getText().trim();
        if (nick.isEmpty()) {
            statusLabel.setText("昵称不能为空");
            return;
        }
        busy = true;
        statusLabel.setText("保存中……");
        new Thread(() -> pendingResult = auth.changeNickname(game.sessionToken(), nick), "cavedream-nick").start();
    }

    /** 用户中心：登出（吊销会话 + 清本地）。 */
    private void doLogout() {
        if (busy) {
            return;
        }
        busy = true;
        loggingOut = true;
        statusLabel.setText("登出中……");
        new Thread(() -> pendingResult = auth.logout(game.sessionToken()), "cavedream-logout").start();
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
        if (mode == Mode.USER && r.ok()) {
            if (loggingOut) {
                loggingOut = false;
                signedInAs = null;
                sessionToken = null;
                game.setSession(null, null);
                statusLabel.setText("已登出");
                setMode(Mode.LOGIN);
            } else if (r.nickname() != null && !r.nickname().isBlank()) {
                signedInAs = r.nickname();
                game.setSession(game.sessionToken(), signedInAs);
                statusLabel.setText("昵称已更新：" + signedInAs);
            }
            return;
        }
        if (r.ok() && r.token() != null) {
            sessionToken = r.token();
            signedInAs = r.nickname() == null ? tfEmail.getText().trim() : r.nickname();
            saveSession(sessionToken, signedInAs);
            setMode(Mode.USER);
        }
    }

    /* ---------------- 会话持久化 ---------------- */

    private void saveSession(String token, String nickname) {
        game.setSession(token, nickname);   // 统一存会话（内存+文件），供 PlayScreen 云存档
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

        // 木质公告牌：菜单按钮包在一块木板里，每个选项是凸起的木条
        float bw = 300f, bh = 52f, bgap = 14f;
        float boardX = W / 2f - bw / 2f - 22f;
        float boardY = menuY(MENU.length - 1) - 40f;
        float boardH = (menuY(0) + 16f) - boardY;
        WoodUi.panel(batch, pixel, boardX, boardY, bw + 44f, boardH);
        for (int i = 0; i < MENU.length; i++) {
            boolean sel = i == selected && !accountOpen;
            float by = menuY(i) - 38f;
            WoodUi.plank(batch, pixel, W / 2f - bw / 2f, by, bw, bh, sel);
            menuFont.setColor(sel ? 1f : 0.82f, sel ? 0.96f : 0.78f, sel ? 0.62f : 0.6f, 1f);
            drawCentered(batch, menuFont, MENU[i], by + bh / 2f + 8f);
            menuFont.setColor(1, 1, 1, 1);
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
        titleFont.dispose();   // 小字集专用，可释放；menuFont/uiFont 为共享缓存，不在此释放
        pixel.dispose();
        moon.dispose();
    }
}
