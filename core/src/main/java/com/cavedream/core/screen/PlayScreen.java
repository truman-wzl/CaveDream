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
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.CaveDreamGame;
import com.cavedream.core.world.BlockType;
import com.cavedream.core.world.LayerGenerator;
import com.cavedream.core.world.LayerWorld;
import com.cavedream.core.world.PlayerEntity;

/**
 * L1 浅梦箱庭游玩界面（M2 垂直切片）：可走、可跳、可挖、可放。
 * 操作：A/D 或 ←/→ 移动，W/空格/↑ 跳；左键挖掘、右键放置（射程 6 格）；ESC 回标题。
 */
public class PlayScreen extends ScreenAdapter implements Disposable {

    private static final int TILE = PlayerEntity.TILE;
    private static final float REACH_TILES = 6f;
    private static final float VIEW_HEIGHT_PX = 720f;

    private final CaveDreamGame game;
    private final LayerWorld world;
    private final PlayerEntity player;
    private SpriteBatch batch;
    private OrthographicCamera camera;
    private Texture pixel;
    private BitmapFont font;
    private BlockType holdBlock = BlockType.DIRT;
    private final Vector3 mouseWorld = new Vector3();

    public PlayScreen(CaveDreamGame game) {
        this.game = game;
        world = LayerGenerator.shallowGarden(20260922L);
        player = new PlayerEntity(
                world.getSpawnTileX() * (float) TILE,
                world.getSpawnTileY() * (float) TILE + 1f);

        batch = new SpriteBatch();
        camera = new OrthographicCamera();
        font = new BitmapFont();
        font.getData().setScale(1.3f);

        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGB888);
        pm.setColor(1, 1, 1, 1);
        pm.fill();
        pixel = new Texture(pm);
        pm.dispose();
    }

    @Override
    public void show() {
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void resize(int width, int height) {
        float aspect = width / (float) Math.max(1, height);
        camera.viewportHeight = VIEW_HEIGHT_PX;
        camera.viewportWidth = VIEW_HEIGHT_PX * aspect;
    }

    @Override
    public void render(float delta) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            game.backToTitle();
            return;
        }
        updateInput();
        player.update(world,
                keyDown(Input.Keys.A) || keyDown(Input.Keys.LEFT),
                keyDown(Input.Keys.D) || keyDown(Input.Keys.RIGHT),
                keyDown(Input.Keys.W) || keyDown(Input.Keys.SPACE) || keyDown(Input.Keys.UP),
                delta);
        updateCamera();

        Gdx.gl.glClearColor(0.043f, 0.055f, 0.10f, 1f);   // 梦夜底色
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        drawVisibleTiles();
        drawPlayer();
        drawCursor();
        batch.end();
        drawHud();
    }

    private boolean keyDown(int keycode) {
        return Gdx.input.isKeyPressed(keycode);
    }

    /* ---------------- 交互：挖掘 / 放置 ---------------- */

    private void updateInput() {
        int[] tile = mouseTile();
        if (tile == null) {
            return;
        }
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            BlockType b = world.blockAt(tile[0], tile[1]);
            if (b != BlockType.AIR && b != BlockType.FOG) {
                world.setBlock(tile[0], tile[1], BlockType.AIR);
                holdBlock = b;   // 挖到什么，手里就拿着什么（MVP 无背包）
            }
        }
        if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
            if (world.blockAt(tile[0], tile[1]) == BlockType.AIR
                    && !player.overlapsTile(tile[0], tile[1])) {
                world.setBlock(tile[0], tile[1], holdBlock);
            }
        }
    }

    /** 鼠标指向的 tile 格；超出挖掘射程或越界返回 null。 */
    private int[] mouseTile() {
        mouseWorld.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        camera.unproject(mouseWorld);
        int tx = (int) Math.floor(mouseWorld.x / TILE);
        int ty = (int) Math.floor(mouseWorld.y / TILE);
        if (!world.inBounds(tx, ty)) {
            return null;
        }
        float dx = tx + 0.5f - player.centerX() / TILE;
        float dy = ty + 0.5f - player.centerY() / TILE;
        return dx * dx + dy * dy <= REACH_TILES * REACH_TILES ? new int[]{tx, ty} : null;
    }

    /* ---------------- 渲染 ---------------- */

    private void updateCamera() {
        float halfW = camera.viewportWidth / 2;
        float halfH = camera.viewportHeight / 2;
        float worldW = world.getWidth() * (float) TILE;
        float worldH = world.getHeight() * (float) TILE;
        camera.position.x = clamp(player.centerX(), halfW, Math.max(halfW, worldW - halfW));
        camera.position.y = clamp(player.centerY(), halfH, Math.max(halfH, worldH - halfH));
        camera.update();
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private void drawVisibleTiles() {
        float halfW = camera.viewportWidth / 2;
        float halfH = camera.viewportHeight / 2;
        int x0 = (int) Math.floor((camera.position.x - halfW) / TILE);
        int x1 = (int) Math.ceil((camera.position.x + halfW) / TILE);
        int y0 = (int) Math.floor((camera.position.y - halfH) / TILE);
        int y1 = (int) Math.ceil((camera.position.y + halfH) / TILE);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                BlockType b = world.blockAt(x, y);
                if (b == BlockType.AIR) {
                    continue;
                }
                batch.setColor(b.r(), b.g(), b.b(), 1f);
                batch.draw(pixel, x * (float) TILE, y * (float) TILE, TILE, TILE);
            }
        }
        batch.setColor(1, 1, 1, 1);
    }

    private void drawPlayer() {
        batch.setColor(0.92f, 0.85f, 1f, 1f);   // 做梦者的轮廓色
        batch.draw(pixel, player.x(), player.y(), player.width(), player.height());
        batch.setColor(1, 1, 1, 1);
    }

    private void drawCursor() {
        int[] tile = mouseTile();
        if (tile != null) {
            batch.setColor(1f, 1f, 0.4f, 0.35f);
            batch.draw(pixel, tile[0] * (float) TILE, tile[1] * (float) TILE, TILE, TILE);
            batch.setColor(1, 1, 1, 1);
        }
    }

    private void drawHud() {
        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        font.setColor(0.85f, 0.88f, 1f, 1f);
        font.draw(batch,
                "L1 Shallow Dream  |  fps " + Gdx.graphics.getFramesPerSecond()
                        + "  |  hold: " + holdBlock.name()
                        + "  |  A/D move  W jump  LMB dig  RMB place  ESC menu",
                camera.position.x - camera.viewportWidth / 2 + 12,
                camera.position.y + camera.viewportHeight / 2 - 16);
        font.setColor(1, 1, 1, 1);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        font.dispose();
        pixel.dispose();
    }
}
