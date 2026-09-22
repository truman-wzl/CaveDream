package com.cavedream.server.web;

import com.cavedream.server.repo.AccountDao;
import com.cavedream.server.service.MailService;
import com.cavedream.server.service.PasswordUtil;
import com.cavedream.server.service.SessionStore;
import com.cavedream.server.service.VerificationCodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 账号 API：发码 / 注册（邮箱验证码）/ 登录 / 找回密码（邮箱验证码）/ 登出。
 * dev-master-code=true 时验证码 888888 可直接通过（授权码配好前走通流程，上线必须关闭）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Pattern EMAIL = Pattern.compile("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$");

    public record SendCodeReq(String email) {
    }

    public record RegisterReq(String email, String code, String password, String nickname) {
    }

    public record LoginReq(String account, String password) {
    }

    public record ResetReq(String email, String code, String newPassword) {
    }

    private final AccountDao accounts;
    private final SessionStore sessions;
    private final MailService mail;
    private final VerificationCodeService codes;
    private final boolean devMasterCode;

    public AuthController(AccountDao accounts, SessionStore sessions, MailService mail,
                          VerificationCodeService codes,
                          @Value("${app.auth.dev-master-code:false}") boolean devMasterCode) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.mail = mail;
        this.codes = codes;
        this.devMasterCode = devMasterCode;
    }

    /** 发送验证码（注册与找回共用）。 */
    @PostMapping("/send-code")
    public ResponseEntity<?> sendCode(@RequestBody SendCodeReq req) {
        String email = trim(req.email());
        if (!EMAIL.matcher(email).matches()) {
            return badRequest("邮箱格式不正确");
        }
        if (!mail.configured()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", devMasterCode
                            ? "邮件服务未配置；可用开发主码 888888"
                            : "邮件服务未配置"));
        }
        String code = codes.issue(email);
        if (code == null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "发送过于频繁，请 60 秒后再试"));
        }
        try {
            mail.sendCode(email, code);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of("error", "邮件发送失败：" + e.getClass().getSimpleName()));
        }
        return ResponseEntity.ok(Map.of("message", "验证码已发送，5 分钟内有效"));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        String email = trim(req.email());
        if (!EMAIL.matcher(email).matches()) {
            return badRequest("邮箱格式不正确");
        }
        if (req.password() == null || req.password().length() < 6) {
            return badRequest("密码至少 6 位");
        }
        if (!codePasses(email, req.code())) {
            return badRequest("验证码错误或已过期");
        }
        if (accounts.findByAccount(email) != null) {
            return conflict("该邮箱已注册");
        }
        String salt = PasswordUtil.newSalt();
        String nick = trim(req.nickname()).isEmpty() ? email.substring(0, email.indexOf('@')) : req.nickname().trim();
        Long id = accounts.create(email, email,
                PasswordUtil.hash(req.password(), salt), salt, "", "", nick);
        return ResponseEntity.ok(Map.of("accountId", id, "message", "账号已创建"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req) {
        String account = trim(req.account());
        Map<String, Object> row = account.isEmpty() ? null : accounts.findByAccount(account);
        if (row == null || !PasswordUtil.matches(req.password(),
                (String) row.get("salt"), (String) row.get("password_hash"))) {
            return unauthorized("邮箱或密码不正确");
        }
        long accountId = ((Number) row.get("id")).longValue();
        accounts.touchLogin(accountId);
        return ResponseEntity.ok(Map.of(
                "token", sessions.issue(accountId),
                "accountId", accountId,
                "nickname", row.get("nickname")));
    }

    /** 找回密码：邮箱验证码通过后重置。 */
    @PostMapping("/reset")
    public ResponseEntity<?> reset(@RequestBody ResetReq req) {
        String email = trim(req.email());
        Map<String, Object> row = EMAIL.matcher(email).matches() ? accounts.findByAccount(email) : null;
        if (row == null) {
            return unauthorized("该邮箱未注册");
        }
        if (!codePasses(email, req.code())) {
            return badRequest("验证码错误或已过期");
        }
        if (req.newPassword() == null || req.newPassword().length() < 6) {
            return badRequest("新密码至少 6 位");
        }
        String salt = PasswordUtil.newSalt();
        accounts.updateCredential(((Number) row.get("id")).longValue(),
                PasswordUtil.hash(req.newPassword(), salt), salt);
        return ResponseEntity.ok(Map.of("message", "密码已重置，请用新密码登录"));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader(value = "X-Token", required = false) String token) {
        sessions.revoke(token);
        return ResponseEntity.ok(Map.of("message", "已登出"));
    }

    /** 验证码校验：dev 主码优先放行（不消费邮箱验证码）。 */
    private boolean codePasses(String email, String code) {
        if (devMasterCode && "888888".equals(trim(code))) {
            return true;
        }
        return codes.verify(email, code);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
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
