package com.cavedream.server.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话表：token → accountId，**落库持久**（重启不失效）+ 内存缓存加速。
 * 早期纯内存实现会在后端重启后清空 token，导致客户端旧 token 失效、云存档列表“消失”；改为 DB 支撑根治。
 */
@Service
public class SessionStore {

    private final JdbcTemplate jdbc;
    private final Map<String, Long> cache = new ConcurrentHashMap<>();

    public SessionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String issue(long accountId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO t_session (token, account_id) VALUES (?, ?)", token, accountId);
        cache.put(token, accountId);
        return token;
    }

    /** token 无效返回 null（先查缓存、未命中回源 DB）。 */
    public Long resolve(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        Long c = cache.get(token);
        if (c != null) {
            return c;
        }
        List<Long> ids = jdbc.queryForList(
                "SELECT account_id FROM t_session WHERE token = ?", Long.class, token);
        if (ids.isEmpty()) {
            return null;
        }
        long id = ids.get(0);
        cache.put(token, id);
        return id;
    }

    public void revoke(String token) {
        if (token == null) {
            return;
        }
        cache.remove(token);
        jdbc.update("DELETE FROM t_session WHERE token = ?", token);
    }
}
