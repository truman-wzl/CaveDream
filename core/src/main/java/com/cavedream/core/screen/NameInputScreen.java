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
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.StretchViewport;
import com.cavedream.core.CaveDreamGame;

import java.util.function.Consumer;

/**
 * 通用命名输入屏：一个文本框 + 确定/取消。用于「新建存档时先命名」与「存档列表里改名」。
 * 确定→onSubmit.accept(名字)（空则回退默认名）；取消/Esc→onCancel.run()。
 */
public class NameInputScreen extends ScreenAdapter implements Disposable {

    private static final float W = 1280f, H = 720f;

    private final CaveDreamGame game;
    private final String title;
    private final String defaultText;
    private final Consumer<String> onSubmit;
    private final Runnable onCancel;

    private final OrthographicCamera cam = new OrthographicCamera();
    private Stage stage;
    private TextField field;
    private BitmapFont font;
    private Texture pixel;

    public NameInputScreen(CaveDreamGame game, String title, String defaultText,
                           Consumer<String> onSubmit, Runnable onCancel) {
        this.game = game;
        this.title = title;
        this.defaultText = defaultText == null ? "" : defaultText;
        this.onSubmit = onSubmit;
        this.onCancel = onCancel;
        pixel = oneWhite();
        font = CjkFonts.get(24);
        build();
    }

    private void build() {
        stage = new Stage(new StretchViewport(W, H, cam));
        Skin skin = new Skin();
        skin.add("white", new TextureRegion(pixel));
        TextureRegionDrawable white = new TextureRegionDrawable(skin.getRegion("white"));

        TextField.TextFieldStyle ts = new TextField.TextFieldStyle();
        ts.font = font;
        ts.fontColor = new Color(0.96f, 0.92f, 0.82f, 1f);
        ts.background = white.tint(new Color(0.14f, 0.10f, 0.06f, 0.96f));
        ts.cursor = white.tint(Color.YELLOW);
        ts.selection = white.tint(new Color(0.5f, 0.38f, 0.16f, 0.6f));

        TextButton.TextButtonStyle bs = new TextButton.TextButtonStyle();
        bs.font = font;
        bs.fontColor = new Color(0.98f, 0.93f, 0.8f, 1f);
        bs.up = white.tint(new Color(0.42f, 0.28f, 0.15f, 1f));
        bs.over = white.tint(new Color(0.60f, 0.42f, 0.21f, 1f));
        bs.down = white.tint(new Color(0.30f, 0.19f, 0.10f, 1f));

        field = new TextField(defaultText, ts);
        field.setMaxLength(24);
        field.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent e, int keycode) {
                if (keycode == Input.Keys.ENTER) {
                    submit();
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                    return true;
                }
                return false;
            }
        });

        TextButton ok = new TextButton("确定", bs);
        ok.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                submit();
            }
        });
        TextButton cancel = new TextButton("取消", bs);
        cancel.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent e, float x, float y) {
                if (onCancel != null) {
                    onCancel.run();
                }
            }
        });

        Label titleLabel = new Label(title, new Label.LabelStyle(font, new Color(0.95f, 0.9f, 0.7f, 1f)));
        titleLabel.setFontScale(1.5f);
        Label hint = new Label("给这个梦起个名字（最多 24 字；直接回车用默认名）",
                new Label.LabelStyle(font, new Color(0.7f, 0.72f, 0.85f, 1f)));

        Drawable panelBg = white.tint(new Color(0.26f, 0.17f, 0.09f, 0.97f));
        Table t = new Table();
        t.setBackground(panelBg);
        t.pad(28);
        t.defaults().spaceBottom(14);
        t.add(titleLabel).row();
        t.add(field).width(460).height(40).row();
        t.add(hint).row();
        Table btns = new Table();
        btns.defaults().spaceRight(12);
        btns.add(ok);
        btns.add(cancel);
        t.add(btns).row();
        t.pack();
        t.setPosition((W - t.getWidth()) / 2f, (H - t.getHeight()) / 2f);
        stage.addActor(t);
    }

    private void submit() {
        String name = field.getText().trim();
        if (name.isEmpty()) {
            name = defaultText.trim().isEmpty() ? "无名之梦" : defaultText.trim();
        }
        if (onSubmit != null) {
            onSubmit.accept(name);
        }
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(stage);
        if (field != null) {
            stage.setKeyboardFocus(field);
            field.setCursorPosition(field.getText().length());
        }
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, false);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(0.05f, 0.04f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(delta);
        stage.draw();
    }

    @Override
    public void hide() {
        Gdx.input.setInputProcessor(null);
    }

    private static Texture oneWhite() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        Texture t = new Texture(pm);
        pm.dispose();
        return t;
    }

    @Override
    public void dispose() {
        stage.dispose();
        pixel.dispose();
    }
}
