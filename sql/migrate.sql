-- 为已有版本补充照片字段，保留业务数据。须在已选择的数据库中执行。
SET @image_column_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'item' AND COLUMN_NAME = 'image_url');
SET @image_migration = IF(@image_column_exists = 0,
    'ALTER TABLE item ADD COLUMN image_url VARCHAR(255) DEFAULT NULL COMMENT ''物品照片地址''',
    'SELECT 1');
PREPARE image_statement FROM @image_migration;
EXECUTE image_statement;
DEALLOCATE PREPARE image_statement;

CREATE TABLE IF NOT EXISTS uploaded_image
(
    filename VARCHAR(64) NOT NULL,
    owner_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (filename),
    KEY idx_uploaded_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='真实图片文件及上传者';
