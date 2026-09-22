-- CaveDream 后端建表脚本（启动自动执行，幂等）
-- 引擎 InnoDB，字符集 utf8mb4（配合库级 utf8mb4_0900_ai_ci）

CREATE TABLE IF NOT EXISTS t_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)  NOT NULL COMMENT '登录名',
    password_hash VARCHAR(128) NOT NULL COMMENT '盐+SHA-256 迭代散列（原型级，上线换 BCrypt）',
    salt          VARCHAR(32)  NOT NULL,
    nickname      VARCHAR(32)  NOT NULL COMMENT '游戏内昵称（可中文）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME     NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '玩家账号';

CREATE TABLE IF NOT EXISTS t_save_slot (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    account_id       BIGINT      NOT NULL,
    slot_no          TINYINT     NOT NULL COMMENT '槽位 1~5',
    save_name        VARCHAR(64) NOT NULL,
    seed             BIGINT      NOT NULL COMMENT '存档种子（梦海确定性生成之根）',
    game_version     VARCHAR(16) NULL COMMENT '客户端版本，做存档迁移判据',
    world_state_json JSON        NOT NULL COMMENT 'WorldState 整包快照（Jackson 序列化，结构演进不改表）',
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_account_slot (account_id, slot_no),
    CONSTRAINT fk_slot_account FOREIGN KEY (account_id) REFERENCES t_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '云存档槽位（本地为权威，云端为备份/跨设备）';

CREATE TABLE IF NOT EXISTS t_cloud_sync_log (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    account_id BIGINT      NOT NULL,
    slot_no    TINYINT     NOT NULL,
    sync_type  VARCHAR(8)  NOT NULL COMMENT 'UP / DOWN',
    bytes      INT         NOT NULL DEFAULT 0,
    synced_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_account_time (account_id, synced_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '同步流水（排查与统计用）';
