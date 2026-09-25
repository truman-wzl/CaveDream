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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 存档列表屏（"继续游戏"）：本地为主存、云端为 cache——同时列出本地档与云端独有档。
 * 点本地档直接进入；点云端独有档 read-through 拉回本地再进。本地行右侧可删除（二次确认）。
 */
public class SaveListScreen extends ScreenAdapter implements Disposable {

    private final CaveDreamGame game;
    private final OrthographicCamera cam = new OrthographicCamera();
    private SpriteBatch batch;
    private Texture pixel;
    private BitmapFont titleFont;
    private BitmapFont font;
    private final GlyphLayout measure = new GlyphLayout();

    private List<GameSave> saves = new ArrayList<>();
    private int hover = -1;
    private float elapsed;                                        // 行内文字过长时的滚动计时
    private int confirmDelete = -1;
    private static final float DEL_W = 108f;
    private static final float RENAME_W = 84f;

    // 云端 cache（异步读）
    private List<Map<String, Object>> cloud = List.of();
    private volatile List<Map<String, Object>> pendingCloud;
    private volatile boolean cloudLoading;
    private volatile GameSave pulledSave;
    private volatile boolean pullFailed;
    private volatile boolean pulling;
    private String status = "";

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
        refreshCloud();
    }

    private void refreshCloud() {
        if (game.sessionToken() == null || cloudLoading) {
            return;
        }
        cloudLoading = true;
        status = "读取云端中……";
        new Thread(() -> {
            pendingCloud = game.cloudList();
        }, "cavedream-cloudlist").start();
    }

    @Override
    public void resize(int width, int height) {
        cam.setToOrtho(false, width, height);
    }

    @Override
    public void render(float delta) {
        consumeAsync();
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.showTitle();
            return;
        }
        float w = cam.viewportWidth, h = cam.viewportHeight;
        elapsed += delta;
        float rowH = 64f, gap = 12f, pad = 16f, btnGap = 10f;
        float panelW = Math.min(560f, w * 0.52f);                 // 文字框宽（不含按钮）
        float totalW = panelW + btnGap + RENAME_W + btnGap + DEL_W;
        float px = w / 2f - totalW / 2f;
        float renX = px + panelW + btnGap;                        // 改名/删除→框外右侧
        float delX = renX + RENAME_W + btnGap;
        float rowsTop = h * 0.72f;
        List<Map<String, Object>> cloudOnly = cloudOnlyList();
        int n = saves.size();
        int c = cloudOnly.size();

        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT) && !pulling) {
            float mx = Gdx.input.getX(), my = h - Gdx.input.getY();
            for (int i = 0; i < n; i++) {
                float y = rowsTop - (i + 1) * (rowH + gap);
                if (my < y || my > y + rowH) {
                    continue;
                }
                if (mx >= delX && mx <= delX + DEL_W) {           // 框外·删除
                    if (confirmDelete == i) {
                        game.deleteSave(saves.get(i).slot);
                        saves = game.listSaves();
                        confirmDelete = -1;
                    } else {
                        confirmDelete = i;
                    }
                    return;
                }
                if (mx >= renX && mx <= renX + RENAME_W) {        // 框外·改名
                    game.promptRename(saves.get(i));
                    return;
                }
                if (mx >= px && mx <= px + panelW) {              // 框内·进入
                    game.continueGame(saves.get(i));
                    return;
                }
            }
            for (int j = 0; j < c; j++) {
                float y = rowsTop - (n + j + 1) * (rowH + gap);
                if (mx >= px && mx <= px + panelW && my >= y && my <= y + rowH) {
                    pullFromCloud(String.valueOf(cloudOnly.get(j).get("slot_key")));
                    return;
                }
            }
            confirmDelete = -1;
            float by = rowsTop - (n + c + 1) * (rowH + gap) - 6f;
            if (mx >= px && mx <= px + panelW && my >= by && my <= by + rowH) {
                game.showTitle();
                return;
            }
        }

        Gdx.gl.glClearColor(0.05f, 0.04f, 0.07f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.setProjectionMatrix(cam.combined);
        batch.begin();
        titleFont.setColor(0.95f, 0.9f, 0.7f, 1f);
        center(titleFont, "梦 之 池", w / 2f, h - 70);
        titleFont.setColor(1, 1, 1, 1);

        if (n == 0 && c == 0) {
            font.setColor(0.7f, 0.72f, 0.85f, 1f);
            center(font, game.sessionToken() == null
                    ? "还没有梦（登录后可从云端拉取；或先进“新的梦”）"
                    : "本地与云端都还没有存档", w / 2f, h / 2f);
            font.setColor(1, 1, 1, 1);
        }
        for (int i = 0; i < n; i++) {
            float y = rowsTop - (i + 1) * (rowH + gap);
            WoodUi.plank(batch, pixel, px, y, panelW, rowH, i == hover);
            font.setColor(1, 1, 1, 1);
            drawScroll(summary(saves.get(i)), px, y, panelW, rowH, pad, y + rowH / 2f + 7f);
            WoodUi.plank(batch, pixel, renX, y + 8, RENAME_W, rowH - 16, false);
            font.setColor(new Color(0.7f, 0.85f, 1f, 1f));
            center(font, "改名", renX + RENAME_W / 2f, y + rowH / 2f + 7f);
            boolean confirming = confirmDelete == i;
            WoodUi.plank(batch, pixel, delX, y + 8, DEL_W, rowH - 16, confirming);
            font.setColor(confirming ? Color.ORANGE : new Color(0.85f, 0.6f, 0.6f, 1f));
            center(font, confirming ? "确认删除?" : "删除", delX + DEL_W / 2f, y + rowH / 2f + 7f);
            font.setColor(1, 1, 1, 1);
        }
        for (int j = 0; j < c; j++) {
            float y = rowsTop - (n + j + 1) * (rowH + gap);
            WoodUi.plank(batch, pixel, px, y, panelW, rowH, hover == n + j);
            font.setColor(0.6f, 0.85f, 1f, 1f);
            drawScroll("[云端] " + cloudSummary(cloudOnly.get(j)), px, y, panelW, rowH, pad, y + rowH / 2f + 7f);
            font.setColor(1, 1, 1, 1);
        }
        float by = rowsTop - (n + c + 1) * (rowH + gap) - 6f;
        WoodUi.plank(batch, pixel, px, by, panelW, rowH, hover == -2);
        font.setColor(1, 1, 1, 1);
        font.draw(batch, "返 回", px + pad, by + rowH / 2f + 7f);

        // 状态行（云端读取/拉取中）
        if (!status.isEmpty()) {
            font.setColor(0.6f, 0.9f, 0.7f, 1f);
            center(font, status, w / 2f, by - 30f);
            font.setColor(1, 1, 1, 1);
        }
        batch.end();
    }

    private void consumeAsync() {
        if (pendingCloud != null) {
            cloud = pendingCloud;
            pendingCloud = null;
            cloudLoading = false;
            status = "云端 " + cloud.size() + " 个档";
        }
        if (pulledSave != null) {
            GameSave s = pulledSave;
            pulledSave = null;
            pulling = false;
            status = "";
            game.continueGame(s);
        } else if (pullFailed) {
            pullFailed = false;
            pulling = false;
            status = "云端拉取失败";
        }
    }

    private void pullFromCloud(String slotKey) {
        if (pulling) {
            return;
        }
        pulling = true;
        status = "从云端拉取中……";
        new Thread(() -> {
            GameSave s = game.cloudPull(slotKey);
            if (s != null) {
                pulledSave = s;
            } else {
                pullFailed = true;
            }
        }, "cavedream-cloudpull").start();
    }

    /** 云端有、本地无的槽位（read-through 候选）。 */
    private List<Map<String, Object>> cloudOnlyList() {
        Set<String> local = new HashSet<>();
        for (GameSave s : saves) {
            if (s.slot != null) {
                local.add(s.slot);
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> m : cloud) {
            if (!local.contains(String.valueOf(m.get("slot_key")))) {
                out.add(m);
            }
        }
        return out;
    }

    private static String cloudSummary(Map<String, Object> m) {
        Object name = m.get("save_name");
        Object ver = m.get("game_version");
        return (name == null ? "云端之梦" : name) + "  ·  （点击拉回本地）"
                + (ver == null ? "" : "  v" + ver);
    }

    private static String summary(GameSave s) {
        String name = (s.saveName == null || s.saveName.isBlank()) ? "未命名之梦" : s.saveName;
        String cn;
        try {
            cn = PlayerClass.valueOf(s.className).cn();
        } catch (Exception e) {
            cn = s.className;
        }
        int hh = (s.clockMinutes / 60) % 24, mm = s.clockMinutes % 60;
        String stage = s.codexOwned ? "已得法典" : "未得法典";
        return "「" + name + "」  " + String.format(
                "%s · 梦眠 %d/%d · 魔能 %d/%d · 铸梦币 %d · %s · 时刻 %02d:%02d",
                cn, s.lucidity, s.maxLucidity, s.mana, s.maxMana, s.coins, stage, hh, mm);
    }

    /** 行内文字：不超宽则静态画；超宽则用 scissor 裁剪、从右向左滚动播放。 */
    private void drawScroll(String text, float boxX, float boxY, float boxW, float boxH, float pad, float baseY) {
        float avail = boxW - pad * 2f;
        measure.setText(font, text);
        float tw = measure.width;
        if (tw <= avail) {
            font.draw(batch, text, boxX + pad, baseY);
            return;
        }
        float loop = tw + avail + 40f;                 // 走完一遍（进右→出左）
        float off = (elapsed * 90f) % loop;            // 滚速 90px/s
        float tx = boxX + avail - off;
        batch.flush();                                 // 先冲掉之前的绘制，确保下面文字在剪刀区内单独提交
        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
        Gdx.gl.glScissor((int) (boxX + pad), (int) boxY, (int) avail, (int) boxH);
        font.draw(batch, text, tx, baseY);
        batch.flush();
        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
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
