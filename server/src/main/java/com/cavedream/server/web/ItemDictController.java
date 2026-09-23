package com.cavedream.server.web;

import com.cavedream.server.repo.ItemDao;
import com.cavedream.server.service.PngUtil;
import com.cavedream.server.service.SessionStore;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 物品词典 API（内容寻址，数据驱动渲染）：
 *  - GET  /api/items                 元数据：物品 + 形态（含 sha/尺寸/权重，不含图片字节）
 *  - GET  /api/items/image/{sha}     按内容哈希取 PNG 字节（强缓存 immutable）
 *  - POST /api/items/{id}/{kind}/{variant}  上传真实手绘 PNG 覆盖同名形态（需 X-Token）
 * 读接口公开无需登录；写接口需登录 token。
 */
@RestController
@RequestMapping("/api/items")
public class ItemDictController {

    private final ItemDao items;
    private final SessionStore sessions;

    public ItemDictController(ItemDao items, SessionStore sessions) {
        this.items = items;
        this.sessions = sessions;
    }

    @GetMapping
    public Map<String, Object> all() {
        List<Map<String, Object>> defs = items.listItems();
        List<Map<String, Object>> forms = items.listFormsMeta();
        for (Map<String, Object> d : defs) {
            long id = ((Number) d.get("id")).longValue();
            List<Map<String, Object>> mine = new ArrayList<>();
            for (Map<String, Object> f : forms) {
                if (((Number) f.get("item_id")).longValue() == id) {
                    Map<String, Object> slim = new LinkedHashMap<>();
                    slim.put("kind", f.get("kind"));
                    slim.put("variant", f.get("variant"));
                    slim.put("weight", f.get("weight"));
                    slim.put("sha", f.get("content_sha"));
                    slim.put("w", f.get("px_w"));
                    slim.put("h", f.get("px_h"));
                    mine.add(slim);
                }
            }
            d.put("forms", mine);
        }
        return Map.of("items", defs);
    }

    @GetMapping("/image/{sha}")
    public ResponseEntity<byte[]> image(@PathVariable String sha) {
        byte[] png = items.findPngBySha(sha);
        if (png == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(png);
    }

    @PostMapping("/{id}/{kind}/{variant}")
    public ResponseEntity<Map<String, Object>> upload(
            @PathVariable int id,
            @PathVariable String kind,
            @PathVariable int variant,
            @RequestHeader(value = "X-Token", required = false) String token,
            @RequestBody byte[] png) {
        if (sessions.resolve(token) == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "需登录 token"));
        }
        if (!PngUtil.isPng(png)) {
            return ResponseEntity.badRequest().body(Map.of("error", "非法 PNG 字节"));
        }
        int[] dims = PngUtil.dims(png);
        String sha = PngUtil.sha256Hex(png);
        try {
            items.upsertFormImage(id, kind, variant, png, sha, dims[0], dims[1]);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.ok(Map.of("sha", sha, "w", dims[0], "h", dims[1]));
    }
}
