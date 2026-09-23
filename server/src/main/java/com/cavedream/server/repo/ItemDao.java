package com.cavedream.server.repo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

/** 物品词典访问层：主表 + 形态子表；形态可携带 PNG 二进制。 */
@Repository
public class ItemDao {

    private final JdbcTemplate jdbc;

    public ItemDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 全部物品定义（不含图片），按 id 升序。 */
    public List<Map<String, Object>> listItems() {
        return jdbc.queryForList(
                "SELECT id, key_name, name_cn, solid, base_rgb, category FROM t_item_def ORDER BY id");
    }

    /** 全部形态定义（不含 png 大字段，仅元数据+哈希+尺寸）。 */
    public List<Map<String, Object>> listFormsMeta() {
        return jdbc.queryForList(
                "SELECT item_id, kind, variant, weight, content_sha, px_w, px_h FROM t_item_form ORDER BY item_id, kind, variant");
    }

    /** 按内容哈希取 PNG 字节；不存在返回 null。 */
    public byte[] findPngBySha(String sha) {
        List<byte[]> rows = jdbc.query(
                "SELECT png FROM t_item_form WHERE content_sha = ? AND png IS NOT NULL LIMIT 1",
                (rs, i) -> rs.getBytes("png"), sha);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 按 (itemId, kind, variant) upsert 一张形态图片；返回该形态行 id。物品不存在则抛异常。 */
    public long upsertFormImage(int itemId, String kind, int variant, byte[] png, String sha, int w, int h) {
        Integer itemIdExists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_item_def WHERE id = ?", Integer.class, itemId);
        if (itemIdExists == null || itemIdExists == 0) {
            throw new IllegalArgumentException("未知物品 id=" + itemId);
        }
        jdbc.update("""
                INSERT INTO t_item_form (item_id, kind, variant, weight, png, px_w, px_h, content_sha)
                VALUES (?, ?, ?, 1.0, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE png = VALUES(png), px_w = VALUES(px_w),
                        px_h = VALUES(px_h), content_sha = VALUES(content_sha)
                """, itemId, kind, variant, png, w, h, sha);
        Long id = jdbc.queryForObject(
                "SELECT id FROM t_item_form WHERE item_id = ? AND kind = ? AND variant = ?",
                Long.class, itemId, kind, variant);
        return id == null ? -1 : id;
    }

    /** 按英文键取物品 id；不存在返回 null。 */
    public Integer itemIdByKey(String keyName) {
        List<Integer> ids = jdbc.queryForList(
                "SELECT id FROM t_item_def WHERE key_name = ?", Integer.class, keyName);
        return ids.isEmpty() ? null : ids.get(0);
    }

    /**
     * 种子灌图（幂等、不覆盖）：仅当目标形态行缺失、或其 png 为 NULL、或 png 在但尺寸缺失时写入。
     * 已完整存在图片（例如真实美术上传：png+尺寸齐备）则保持不变，返回 false。
     */
    public boolean seedImageIfAbsent(int itemId, String kind, int variant, byte[] png, String sha, int w, int h) {
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_item_def WHERE id = ?", Integer.class, itemId);
        if (exists == null || exists == 0) {
            return false;
        }
        // 先试更新：行已存在且（无图 或 尺寸缺失）时补齐（避开 MySQL ON DUPLICATE 列序求值坑）
        int updated = jdbc.update(
                "UPDATE t_item_form SET png = ?, px_w = ?, px_h = ?, content_sha = ? "
                        + "WHERE item_id = ? AND kind = ? AND variant = ? AND (png IS NULL OR px_w IS NULL)",
                png, w, h, sha, itemId, kind, variant);
        if (updated > 0) {
            return true;
        }
        // 行不存在则插入；行已存在且有完整图 → 唯一键冲突被 IGNORE 跳过（不覆盖）
        int inserted = jdbc.update(
                "INSERT IGNORE INTO t_item_form (item_id, kind, variant, weight, png, px_w, px_h, content_sha) "
                        + "VALUES (?, ?, ?, 1.0, ?, ?, ?, ?)",
                itemId, kind, variant, png, w, h, sha);
        return inserted > 0;
    }
}
