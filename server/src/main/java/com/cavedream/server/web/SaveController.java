package com.cavedream.server.web;

import com.cavedream.server.repo.SaveDao;
import com.cavedream.server.service.SessionStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 云存档槽位 CRUD（本地为权威，云端为备份/跨设备）。
 * 所有接口需请求头 X-Token（登录获得）。world_state_json 为客户端 Jackson 序列化的整包 JSON。
 */
@RestController
@RequestMapping("/api/saves")
public class SaveController {

    public record SaveReq(String saveName, long seed, String gameVersion, String worldStateJson) {
    }

    private final SaveDao saves;
    private final SessionStore sessions;

    public SaveController(SaveDao saves, SessionStore sessions) {
        this.saves = saves;
        this.sessions = sessions;
    }

    @GetMapping
    public ResponseEntity<?> list(@RequestHeader(value = "X-Token", required = false) String token) {
        Long accountId = resolve(token);
        if (accountId == null) {
            return unauthorized();
        }
        List<Map<String, Object>> slots = saves.list(accountId);
        return ResponseEntity.ok(Map.of("slots", slots));
    }

    @GetMapping("/{slotNo}")
    public ResponseEntity<?> download(@RequestHeader(value = "X-Token", required = false) String token,
                                      @PathVariable int slotNo) {
        Long accountId = resolve(token);
        if (accountId == null) {
            return unauthorized();
        }
        Map<String, Object> slot = saves.findSlot(accountId, slotNo);
        if (slot == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "该槽位没有梦"));
        }
        saves.logSync(accountId, slotNo, "DOWN",
                slot.get("world_state_json") == null ? 0
                        : String.valueOf(slot.get("world_state_json")).getBytes(StandardCharsets.UTF_8).length);
        return ResponseEntity.ok(slot);
    }

    @PutMapping("/{slotNo}")
    public ResponseEntity<?> upload(@RequestHeader(value = "X-Token", required = false) String token,
                                    @PathVariable int slotNo,
                                    @RequestBody SaveReq req) {
        Long accountId = resolve(token);
        if (accountId == null) {
            return unauthorized();
        }
        if (slotNo < 1 || slotNo > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "槽位号限 1~5"));
        }
        if (req.worldStateJson() == null || req.worldStateJson().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "worldStateJson 不能为空"));
        }
        saves.upsert(accountId, slotNo,
                req.saveName() == null ? "dream-" + slotNo : req.saveName(),
                req.seed(), req.gameVersion(), req.worldStateJson());
        saves.logSync(accountId, slotNo, "UP",
                req.worldStateJson().getBytes(StandardCharsets.UTF_8).length);
        return ResponseEntity.ok(Map.of("message", "已存入云端之梦", "slotNo", slotNo));
    }

    @DeleteMapping("/{slotNo}")
    public ResponseEntity<?> delete(@RequestHeader(value = "X-Token", required = false) String token,
                                    @PathVariable int slotNo) {
        Long accountId = resolve(token);
        if (accountId == null) {
            return unauthorized();
        }
        int n = saves.delete(accountId, slotNo);
        return ResponseEntity.ok(Map.of("deleted", n));
    }

    private Long resolve(String token) {
        return sessions.resolve(token);
    }

    private static ResponseEntity<Map<String, String>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "未登录或 token 失效"));
    }
}
