package com.cavedream.server.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码：5 分钟有效、60 秒重发限制、验证通过即失效。
 * 原型为内存存储（重启失效）；正式版换 Redis + TTL。
 */
@Service
public class VerificationCodeService {

    private record Entry(String code, long expiresAt, long lastSentAt) {
    }

    private static final long TTL_MS = 5 * 60_000L;
    private static final long RESEND_MS = 60_000L;

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /**
     * 发放验证码。
     *
     * @return 新验证码；若距上次发送不足 60 秒返回 null（调用方应回 429）
     */
    public String issue(String email) {
        String key = email.toLowerCase();
        long now = System.currentTimeMillis();
        Entry old = store.get(key);
        if (old != null && now - old.lastSentAt() < RESEND_MS) {
            return null;
        }
        String code = String.format("%06d", random.nextInt(1_000_000));
        store.put(key, new Entry(code, now + TTL_MS, now));
        return code;
    }

    /** 校验并消费（一次性）。 */
    public boolean verify(String email, String code) {
        String key = email.toLowerCase();
        Entry e = store.get(key);
        if (e == null || System.currentTimeMillis() > e.expiresAt()
                || !e.code().equals(code == null ? "" : code.trim())) {
            return false;
        }
        store.remove(key);
        return true;
    }
}
