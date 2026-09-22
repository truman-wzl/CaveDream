package com.cavedream.server.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** 云存档槽位访问层：一账号一槽位唯一，上传即 upsert。 */
@Repository
public class SaveDao {

    private final JdbcTemplate jdbc;

    public SaveDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(long accountId, int slotNo, String saveName, long seed,
                       String gameVersion, String worldStateJson) {
        jdbc.update("""
                INSERT INTO t_save_slot (account_id, slot_no, save_name, seed, game_version, world_state_json)
                VALUES (?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                  save_name = VALUES(save_name),
                  seed = VALUES(seed),
                  game_version = VALUES(game_version),
                  world_state_json = VALUES(world_state_json),
                  updated_at = NOW()
                """,
                accountId, slotNo, saveName, seed, gameVersion, worldStateJson);
    }

    public List<Map<String, Object>> list(long accountId) {
        return jdbc.queryForList("""
                SELECT slot_no, save_name, seed, game_version,
                       JSON_LENGTH(world_state_json) IS NOT NULL AS has_state,
                       updated_at
                FROM t_save_slot WHERE account_id = ? ORDER BY slot_no
                """, accountId);
    }

    /** 下载整包（含 world_state_json）；不存在返回 null。 */
    public Map<String, Object> findSlot(long accountId, int slotNo) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT slot_no, save_name, seed, game_version,
                       CAST(world_state_json AS CHAR) AS world_state_json, updated_at
                FROM t_save_slot WHERE account_id = ? AND slot_no = ?
                """, accountId, slotNo);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public int delete(long accountId, int slotNo) {
        return jdbc.update("DELETE FROM t_save_slot WHERE account_id = ? AND slot_no = ?", accountId, slotNo);
    }

    public void logSync(long accountId, int slotNo, String type, int bytes) {
        jdbc.update("INSERT INTO t_cloud_sync_log (account_id, slot_no, sync_type, bytes) VALUES (?, ?, ?, ?)",
                accountId, slotNo, type, bytes);
    }
}
