package com.cavedream.server.web;

import com.cavedream.server.repo.AccountDao;
import com.cavedream.server.service.PasswordUtil;
import com.cavedream.server.service.SessionStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 注册 / 登录 / 登出。 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record CredReq(String username, String password, String nickname) {
    }

    private final AccountDao accounts;
    private final SessionStore sessions;

    public AuthController(AccountDao accounts, SessionStore sessions) {
        this.accounts = accounts;
        this.sessions = sessions;
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody CredReq req) {
        if (req.username() == null || req.username().length() < 3 || req.username().length() > 32) {
            return badRequest("用户名需 3~32 字符");
        }
        if (req.password() == null || req.password().length() < 6) {
            return badRequest("密码至少 6 位");
        }
        if (accounts.findByUsername(req.username()) != null) {
            return conflict("用户名已被占用");
        }
        String salt = PasswordUtil.newSalt();
        Long id = accounts.create(req.username(),
                PasswordUtil.hash(req.password(), salt), salt,
                req.nickname() == null ? req.username() : req.nickname());
        return ResponseEntity.ok(Map.of("accountId", id, "message", "入梦凭证已就绪"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody CredReq req) {
        Map<String, Object> row = req.username() == null ? null : accounts.findByUsername(req.username());
        if (row == null || !PasswordUtil.matches(req.password(),
                (String) row.get("salt"), (String) row.get("password_hash"))) {
            return unauthorized("用户名或密码不对，再睡一觉想想");
        }
        long accountId = ((Number) row.get("id")).longValue();
        accounts.touchLogin(accountId);
        return ResponseEntity.ok(Map.of(
                "token", sessions.issue(accountId),
                "accountId", accountId,
                "nickname", row.get("nickname")));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "X-Token", required = false) String token) {
        sessions.revoke(token);
        return ResponseEntity.ok(Map.of("message", "已登出"));
    }

    private static ResponseEntity<Map<String, String>> badRequest(String msg) {
        return ResponseEntity.badRequest().body(Map.of("error", msg));
    }

    private static ResponseEntity<Map<String, String>> conflict(String msg) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", msg));
    }

    private static ResponseEntity<Map<String, String>> unauthorized(String msg) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", msg));
    }
}
