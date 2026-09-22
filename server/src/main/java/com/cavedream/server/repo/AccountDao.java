package com.cavedream.server.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** 账号表访问层（JdbcTemplate，原型期不引 ORM）。 */
@Repository
public class AccountDao {

    private final JdbcTemplate jdbc;

    public AccountDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 返回新账号 id；username 重复时抛异常（唯一索引兜底）。 */
    public Long create(String username, String email, String passwordHash, String salt,
                       String secQuestion, String secAnswerHash, String nickname) {
        jdbc.update(
                "INSERT INTO t_account (username, email, password_hash, salt, sec_question, sec_answer, nickname) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?)",
                username, email, passwordHash, salt, secQuestion, secAnswerHash, nickname);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 按用户名或邮箱查账号（登录用）；不存在返回 null。 */
    public Map<String, Object> findByAccount(String account) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username, email, password_hash, salt, sec_question, sec_answer, nickname "
                        + "FROM t_account WHERE username = ? OR email = ?",
                account, account);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 查登录/找回所需字段；不存在返回 null。 */
    public Map<String, Object> findByUsername(String username) {
        return findByAccount(username);
    }

    /** 找回密码：重置口令散列与盐（密保保持不变，可继续用于下次找回）。 */
    public void updateCredential(long accountId, String passwordHash, String salt) {
        jdbc.update("UPDATE t_account SET password_hash = ?, salt = ? WHERE id = ?",
                passwordHash, salt, accountId);
    }

    public void touchLogin(long accountId) {
        jdbc.update("UPDATE t_account SET last_login_at = NOW() WHERE id = ?", accountId);
    }
}
