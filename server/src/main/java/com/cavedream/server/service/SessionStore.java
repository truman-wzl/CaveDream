package com.cavedream.server.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存会话表：token → accountId。
 * 原型方案（重启即失效）；正式版换 JWT 或 Redis，Controller 侧只依赖 resolve()。
 */
@Service
public class SessionStore {

    private final Map<String, Long> tokens = new ConcurrentHashMap<>();

    public String issue(long accountId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, accountId);
        return token;
    }

    /** token 无效返回 null。 */
    public Long resolve(String token) {
        return token == null ? null : tokens.get(token);
    }

    public void revoke(String token) {
        if (token != null) {
            tokens.remove(token);
        }
    }
}
