-- CaveDream 后端建表脚本（启动自动执行，幂等）
-- 引擎 InnoDB，字符集 utf8mb4（配合库级 utf8mb4_0900_ai_ci）

CREATE TABLE IF NOT EXISTS t_account (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)  NOT NULL COMMENT '登录名',
    email         VARCHAR(64)  NULL COMMENT '邮箱（验证码注册/找回；也可作登录账号）',
    UNIQUE KEY uk_email (email),
    password_hash VARCHAR(128) NOT NULL COMMENT '盐+SHA-256 迭代散列（原型级，上线换 BCrypt）',
    salt          VARCHAR(32)  NOT NULL,
    sec_question  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '密保问题（找回密码用）',
    sec_answer    VARCHAR(128) NOT NULL DEFAULT '' COMMENT '密保答案散列（小写去空格后同法散列）',
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

-- 存量库自动迁移：旧表补密保列（重复执行报 1060 重复列，由 continue-on-error 忽略）
ALTER TABLE t_account ADD COLUMN sec_question VARCHAR(128) NOT NULL DEFAULT '' AFTER salt;
ALTER TABLE t_account ADD COLUMN sec_answer   VARCHAR(128) NOT NULL DEFAULT '' AFTER sec_question;
ALTER TABLE t_account ADD COLUMN email VARCHAR(64) NULL AFTER username;
ALTER TABLE t_account ADD UNIQUE KEY uk_email (email);

-- ============ 物品词典（物品ID主表 + 形态子表；图片以 PNG 二进制入库，数据驱动渲染） ============
-- 设计：每个物品(t_item_def) 关联若干“形态”(t_item_form)，每形态可携带一张 PNG 贴图（LONGBLOB）。
-- 渲染时客户端拉取→烘成图集→按 (邻居, 权重, 内容哈希) 选形作画；离线回退程序生成。
-- 美术来源两条：① 启动时 ItemSeeder 从 classpath:assets/items/*.png 幂等灌库（程序生成占位）；
--             ② POST /api/items/{id}/{kind}/{variant} 上传真实手绘 PNG 覆盖同名形态。

-- 存量库迁移：旧词典表 t_block_* 仅含种子数据（无用户内容），直接丢弃重建为 t_item_*
DROP TABLE IF EXISTS t_block_form;
DROP TABLE IF EXISTS t_block_def;

CREATE TABLE IF NOT EXISTS t_item_def (
    id        INT          NOT NULL COMMENT '物品全局ID（与客户端 BlockType.id 一致）',
    key_name  VARCHAR(32)  NOT NULL COMMENT '英文键（AIR/DIRT/…）',
    name_cn   VARCHAR(32)  NOT NULL COMMENT '中文名',
    solid     TINYINT(1)   NOT NULL DEFAULT 1,
    base_rgb  CHAR(6)      NOT NULL COMMENT '基色 hex（如 6B4A2F）',
    category  VARCHAR(16)  NOT NULL DEFAULT 'NATURAL' COMMENT 'NATURAL/STRUCTURE/SPECIAL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_key (key_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '物品词典主表';

CREATE TABLE IF NOT EXISTS t_item_form (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    item_id     INT         NOT NULL COMMENT 'FK→t_item_def.id',
    kind        VARCHAR(16) NOT NULL COMMENT 'fill填充/lip草唇/blob圆坨/wave水纹…',
    variant     INT         NOT NULL DEFAULT 0 COMMENT '变体序号（同 kind 多形态）',
    weight      DOUBLE      NOT NULL DEFAULT 1.0 COMMENT '抽取权重',
    params      JSON        NULL COMMENT '渲染参数（噪声尺度/描边色/侵蚀概率等，程序回退用）',
    png         LONGBLOB    NULL COMMENT '该形态的 PNG 贴图字节（NULL 表示尚无图，客户端回退程序生成）',
    px_w        SMALLINT    NULL COMMENT '图片像素宽',
    px_h        SMALLINT    NULL COMMENT '图片像素高',
    content_sha CHAR(64)    NULL COMMENT 'png 的 SHA-256（十六进制）；去重 + 客户端缓存失效判据',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_kind_variant (item_id, kind, variant),
    KEY idx_sha (content_sha),
    CONSTRAINT fk_form_item FOREIGN KEY (item_id) REFERENCES t_item_def (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '形态子表：每物品关联若干可渲染形态（含图片）';

-- 种子数据：13 种物品 + 形态池（客户端首启拉取后缓存，离线用内置默认）
INSERT IGNORE INTO t_item_def (id, key_name, name_cn, solid, base_rgb, category) VALUES
 (0,'AIR','空气',0,'000000','NATURAL'),
 (1,'DIRT','泥土',1,'6B4A2F','NATURAL'),
 (2,'GRASS','草方块',1,'4C8A3E','NATURAL'),
 (3,'STONE','石头',1,'55525E','NATURAL'),
 (4,'DEEP_SEED','沉眠矿',1,'49C7C9','NATURAL'),
 (5,'WOOD','木材',1,'8A6234','NATURAL'),
 (6,'LEAF','树叶',1,'2F6B2A','NATURAL'),
 (7,'FOG','梦之雾',1,'2A2140','SPECIAL'),
 (8,'WATER','梦水',0,'2E6FBF','NATURAL'),
 (9,'HELL','梦滓',1,'3A1F1F','NATURAL'),
 (10,'MUSHROOM','幻菇',1,'7E5AA0','NATURAL'),
 (11,'CLOUD','云絮',1,'B9A7E0','NATURAL'),
 (12,'POT','陶罐',1,'B0703F','STRUCTURE'),
 (13,'IRON_ORE','铁矿',1,'9AA0A8','NATURAL'),
 (14,'GOLD_ORE','金矿',1,'D9A94A','NATURAL'),
 (15,'PLATINUM_ORE','白金矿',1,'DCE6EF','NATURAL'),
 (16,'SAINT_ORE','圣矿',1,'B46BE6','NATURAL'),
 (100,'PICKAXE_WOOD','木镐',0,'8A5A2B','TOOL'),
 (101,'PICKAXE_STONE','石镐',0,'9AA0A8','TOOL'),
 (102,'PICKAXE_IRON','铁镐',0,'B8BCC8','TOOL'),
 (103,'PICKAXE_GOLD','金镐',0,'E6C34A','TOOL'),
 (104,'PICKAXE_PLATINUM','白金镐',0,'EAE6F0','TOOL'),
 (105,'PICKAXE_SAINT','圣镐',0,'C77BF0','TOOL'),
 (110,'AXE_WOOD','木斧',0,'8A5A2B','TOOL'),
 (111,'AXE_STONE','石斧',0,'9AA0A8','TOOL'),
 (112,'AXE_IRON','铁斧',0,'B8BCC8','TOOL'),
 (113,'AXE_GOLD','金斧',0,'E6C34A','TOOL'),
 (114,'AXE_PLATINUM','白金斧',0,'EAE6F0','TOOL'),
 (115,'AXE_SAINT','圣斧',0,'C77BF0','TOOL'),
 (200,'WEAPON_WARRIOR','战士主武器',0,'C0C4CC','WEAPON'),
 (201,'WEAPON_MAGE','法师主武器',0,'7C5AA6','WEAPON'),
 (202,'WEAPON_SUMMONER','通灵者主武器',0,'4FA38A','WEAPON'),
 (203,'WEAPON_ARCHER','射手主武器',0,'A8703A','WEAPON'),
 (204,'WEAPON_ASSASSIN','刺客主武器',0,'3A3A52','WEAPON');

-- 每种方块 4 个 fill 变体；草块另有 lip/blob；水有 wave；雾有 swirl（png 由 ItemSeeder/上传填充）
INSERT IGNORE INTO t_item_form (item_id, kind, variant, weight, params) VALUES
 (1,'fill',0,1.0,NULL),(1,'fill',1,1.0,NULL),(1,'fill',2,1.0,NULL),(1,'fill',3,1.0,NULL),
 (2,'fill',0,1.0,NULL),(2,'fill',1,1.0,NULL),(2,'fill',2,1.0,NULL),(2,'fill',3,1.0,NULL),
 (2,'lip',0,1.0,'{"height":8,"wave":0.55}'),(2,'blob',0,1.0,'{"radius":7}'),
 (3,'fill',0,1.0,NULL),(3,'fill',1,1.0,NULL),(3,'fill',2,1.0,NULL),(3,'fill',3,1.0,NULL),
 (4,'fill',0,1.0,'{"crystal":0.14}'),(4,'fill',1,1.0,'{"crystal":0.14}'),
 (5,'fill',0,1.0,'{"stripe":4}'),(5,'fill',1,1.0,'{"stripe":4}'),
 (6,'fill',0,1.0,NULL),(6,'fill',1,1.0,NULL),
 (7,'fill',0,1.0,'{"cellNoise":4}'),(7,'swirl',0,0.2,'{"speed":0.3}'),
 (8,'fill',0,1.0,NULL),(8,'wave',0,1.0,'{"amp":0.35}'),
 (9,'fill',0,1.0,'{"ember":0.05}'),(9,'fill',1,1.0,'{"ember":0.05}'),
 (10,'fill',0,1.0,NULL),(11,'fill',0,1.0,'{"soft":0.24}'),
 (12,'fill',0,1.0,'{"band":[4,11]}');
