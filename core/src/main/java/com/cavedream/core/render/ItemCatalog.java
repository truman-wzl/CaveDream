package com.cavedream.core.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.cavedream.core.world.BlockType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 物品词典客户端（内容寻址，数据驱动渲染）：
 *  1) 后台线程 GET /api/items 取元数据（每形态含 sha）；
 *  2) 逐 sha 检查磁盘缓存 ~/.cavedream/items/&lt;sha&gt;.png，缺则 GET /api/items/image/{sha} 下载落盘；
 *  3) 回 GL 主线程把缓存 PNG 建成 Texture 并按世界坐标裁 16×16 子块（与程序生成同观感）。
 * 离线/后端未起：ready 保持 false，渲染回退 {@link BlockTextures} 程序生成；下次联网自动补齐。
 */
public final class ItemCatalog implements Disposable {

    private static final int TILE = BlockTextures.TILE;
    private static final int SHEET = BlockTextures.SHEET_SIZE;
    private static final FileHandle CACHE_DIR = Gdx.files.external("cavedream/items");

    private static ItemCatalog instance;

    public static ItemCatalog get() {
        return instance;
    }

    /** 启动异步加载；须在 GL 线程（游戏 create/show）调用。 */
    public static void startAsync(String baseUrl) {
        if (instance == null) {
            instance = new ItemCatalog(baseUrl);
        }
        instance.refreshAsync();
    }

    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = new ObjectMapper();

    private final Texture[] sheets = new Texture[BlockType.values().length];
    private final TextureRegion[][][] regions =
            new TextureRegion[BlockType.values().length][4][4];
    private volatile Texture grassLip;
    private volatile Texture grassBlob;
    private volatile boolean ready;

    private ItemCatalog(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean ready() {
        return ready;
    }

    /** 该材料是否有来自 DB 的贴图（否则调用方回退程序生成）。 */
    public boolean has(BlockType b) {
        return sheets[b.ordinal()] != null;
    }

    /** 世界坐标 (x,y) 处来自 DB 的贴图子块；无则 null。 */
    public TextureRegion region(BlockType b, int x, int y) {
        Texture t = sheets[b.ordinal()];
        if (t == null) {
            return null;
        }
        return regions[b.ordinal()][Math.floorMod(x, 4)][Math.floorMod(y, 4)];
    }

    public Texture grassLip() {
        return grassLip;
    }

    public Texture grassBlob() {
        return grassBlob;
    }

    /** 拉取 + 下载缓存（后台线程），完成后回 GL 线程建纹理。 */
    private void refreshAsync() {
        new Thread(this::load, "cavedream-itemcatalog").start();
    }

    private void load() {
        try {
            HttpResponse<String> meta = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/items"))
                            .timeout(Duration.ofSeconds(6)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (meta.statusCode() != 200) {
                Gdx.app.log("ItemCatalog", "词典 HTTP " + meta.statusCode() + "，回退程序生成");
                return;
            }
            // key -> {fill, lip, blob} 的 sha
            Map<BlockType, String[]> byBlock = new LinkedHashMap<>();
            JsonNode root = json.readTree(meta.body());
            for (JsonNode item : root.path("items")) {
                BlockType b = blockOf(item.path("key_name").asText(""));
                if (b == null) {
                    continue;
                }
                String fill = null, lip = null, blob = null;
                for (JsonNode f : item.path("forms")) {
                    String sha = text(f.path("sha"));
                    if (sha == null) {
                        continue;
                    }
                    switch (f.path("kind").asText("")) {
                        case "fill" -> {
                            if (f.path("variant").asInt(0) == 0) {
                                fill = sha;
                            }
                        }
                        case "lip" -> lip = sha;
                        case "blob" -> blob = sha;
                        default -> { }
                    }
                }
                if (fill != null || lip != null || blob != null) {
                    byBlock.put(b, new String[]{fill, lip, blob});
                }
            }
            if (byBlock.isEmpty()) {
                Gdx.app.log("ItemCatalog", "词典无带图形态（可能尚未灌库），回退程序生成");
                return;
            }
            // 逐 sha 确保磁盘缓存存在
            for (String[] shas : byBlock.values()) {
                for (String sha : shas) {
                    if (sha != null) {
                        ensureCached(sha);
                    }
                }
            }
            final Map<BlockType, String[]> snapshot = byBlock;
            Gdx.app.postRunnable(() -> buildOnGlThread(snapshot));
        } catch (Exception e) {
            Gdx.app.log("ItemCatalog", "离线/异常：" + e.getClass().getSimpleName() + "，回退程序生成");
        }
    }

    private void buildOnGlThread(Map<BlockType, String[]> byBlock) {
        for (Map.Entry<BlockType, String[]> e : byBlock.entrySet()) {
            BlockType b = e.getKey();
            String[] shas = e.getValue();
            if (shas[0] != null && sheets[b.ordinal()] == null) {
                Texture t = loadTexture(shas[0]);
                if (t != null) {
                    t.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
                    sheets[b.ordinal()] = t;
                    for (int i = 0; i < 4; i++) {
                        for (int j = 0; j < 4; j++) {
                            regions[b.ordinal()][i][j] = new TextureRegion(t, i * TILE, j * TILE, TILE, TILE);
                        }
                    }
                }
            }
            if (b == BlockType.GRASS) {
                if (shas[1] != null && grassLip == null) {
                    grassLip = loadTexture(shas[1]);
                }
                if (shas[2] != null && grassBlob == null) {
                    grassBlob = loadTexture(shas[2]);
                }
            }
        }
        ready = true;
        Gdx.app.log("ItemCatalog", "来自 DB 的物品图集已就绪");
    }

    private Texture loadTexture(String sha) {
        FileHandle f = cacheFile(sha);
        if (!f.exists()) {
            return null;
        }
        try {
            return new Texture(f);
        } catch (Exception ex) {
            Gdx.app.log("ItemCatalog", "解码失败 sha=" + sha + "：" + ex.getClass().getSimpleName());
            return null;
        }
    }

    private void ensureCached(String sha) {
        FileHandle f = cacheFile(sha);
        if (f.exists()) {
            return;
        }
        try {
            HttpResponse<byte[]> img = http.send(
                    HttpRequest.newBuilder(URI.create(baseUrl + "/api/items/image/" + sha))
                            .timeout(Duration.ofSeconds(8)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (img.statusCode() == 200 && img.body().length > 0) {
                f.parent().mkdirs();
                f.writeBytes(img.body(), false);
            }
        } catch (Exception e) {
            Gdx.app.log("ItemCatalog", "图片下载失败 sha=" + sha);
        }
    }

    private static FileHandle cacheFile(String sha) {
        return CACHE_DIR.child(sha + ".png");
    }

    private static BlockType blockOf(String key) {
        try {
            return BlockType.valueOf(key);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String text(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }

    @Override
    public void dispose() {
        for (Texture t : sheets) {
            if (t != null) {
                t.dispose();
            }
        }
        if (grassLip != null) {
            grassLip.dispose();
        }
        if (grassBlob != null) {
            grassBlob.dispose();
        }
    }
}
