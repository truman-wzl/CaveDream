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
    public Long create(String username, String passwordHash, String salt, String nickname) {
        jdbc.update(
                "INSERT INTO t_account (username, password_hash, salt, nickname) VALUES (?, ?, ?, ?)",
                username, passwordHash, salt, nickname);
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    /** 查登录所需字段；不存在返回 null。 */
    public Map<String, Object> findByUsername(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, username, password_hash, salt, nickname FROM t_account WHERE username = ?",
                username);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void touchLogin(long accountId) {
        jdbc.update("UPDATE t_account SET last_login_at = NOW() WHERE id = ?", accountId);
    }
}
