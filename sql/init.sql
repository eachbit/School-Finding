CREATE DATABASE IF NOT EXISTS school_finding
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_general_ci;

USE school_finding;

DROP TABLE IF EXISTS claim;
DROP TABLE IF EXISTS op_log;
DROP TABLE IF EXISTS item;
DROP TABLE IF EXISTS sys_user;

CREATE TABLE sys_user
(
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username   VARCHAR(50)  NOT NULL COMMENT '登录名（唯一）',
    password   VARCHAR(100) NOT NULL COMMENT '密码哈希（BCrypt）',
    role       VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT '角色：STUDENT=学生, ADMIN=管理员',
    contact    VARCHAR(100) DEFAULT NULL COMMENT '联系方式',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '用户表';

CREATE TABLE item
(
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    type            VARCHAR(16)   NOT NULL COMMENT '类型：LOST=失物, FOUND=拾物',
    title           VARCHAR(100)  NOT NULL COMMENT '标题',
    description     VARCHAR(1000) NOT NULL COMMENT '描述',
    category        VARCHAR(32)   NOT NULL COMMENT '类别，如 雨具/电子设备/证件',
    location        VARCHAR(100)  NOT NULL COMMENT '丢失或拾取地点',
    happen_time     DATETIME      NOT NULL COMMENT '丢失/拾取时间',
    color           VARCHAR(32)   DEFAULT NULL COMMENT '颜色（可选，未知填 NULL）',
    contact         VARCHAR(100)  NOT NULL COMMENT '联系方式（不对外公开）',
    hidden_feature  VARCHAR(200)  DEFAULT NULL COMMENT '隐藏特征（仅管理员可见）',
    verify_question VARCHAR(200)  DEFAULT NULL COMMENT '认领验证问题（可展示给认领者）',
    verify_answer   VARCHAR(100)  DEFAULT NULL COMMENT '验证参考答案（仅管理员可见）',
    status          VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待审核, PUBLIC=公开, REJECTED=已驳回, CLOSED=已关闭, CLAIMED=已领取',
    publisher_id    BIGINT        NOT NULL COMMENT '发布者用户 id',
    review_opinion  VARCHAR(200)  DEFAULT NULL COMMENT '审核意见',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_item_status_type (status, type),
    KEY idx_item_publisher (publisher_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '物品信息表（失物/拾物）';

CREATE TABLE claim
(
    id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    item_id        BIGINT        NOT NULL COMMENT '物品 id',
    applicant_id   BIGINT        NOT NULL COMMENT '申请人用户 id',
    proof          VARCHAR(1000) NOT NULL COMMENT '证明文字',
    answer         VARCHAR(100)  DEFAULT NULL COMMENT '对验证问题的回答',
    status         VARCHAR(16)   NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING=待审核, APPROVED=审核通过, REJECTED=已驳回, CANCELLED=已取消, HANDED=已领取, REVOKED=管理员撤销',
    review_opinion VARCHAR(200)  DEFAULT NULL COMMENT '审核意见 / 撤销原因',
    reviewer_id    BIGINT        DEFAULT NULL COMMENT '审核人（管理员）id',
    handover_by    BIGINT        DEFAULT NULL COMMENT '实际交接确认人（管理员）id',
    created_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_claim_item (item_id),
    KEY idx_claim_applicant (applicant_id),
    KEY idx_claim_status (status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '认领申请表';

CREATE TABLE op_log
(
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    operator_id   BIGINT       NOT NULL COMMENT '操作人 id',
    operator_name VARCHAR(50)  NOT NULL COMMENT '操作人用户名（冗余一份，方便直接展示）',
    action        VARCHAR(32)  NOT NULL COMMENT '操作类型：ITEM_REVIEW / CLAIM_SUBMIT / CLAIM_CANCEL / CLAIM_REVIEW / CLAIM_REVOKE / CLAIM_HANDOVER',
    target_type   VARCHAR(16)  NOT NULL COMMENT '目标类型：ITEM / CLAIM',
    target_id     BIGINT       NOT NULL COMMENT '目标 id',
    opinion       VARCHAR(500) DEFAULT NULL COMMENT '意见 / 说明',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_log_created (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT '操作日志表';

INSERT INTO sys_user (id, username, password, role, contact, created_at)
VALUES (1, 'student1', '$2a$10$LZ8z7gLX2lS58yB9I3i2cODdqfU86dFWN6wxLsUW/peS9RVHVjE6.', 'STUDENT', '13800000001', NOW()),
       (2, 'student2', '$2a$10$MZuiprixl9f8Gbg7l.HheOUd8kfo6FLHs536Wr0zjjy4qHb906V6O', 'STUDENT', '13800000002', NOW()),
       (3, 'admin', '$2a$10$krrNrFCuE3PaAwuBZv4giODfXDYXxW.UNnI4O5JFE1dmOgSgWlkHq', 'ADMIN', '13900000000', NOW());

INSERT INTO item (id, type, title, description, category, location, happen_time, color, contact,
                  hidden_feature, verify_question, verify_answer, status, publisher_id, review_opinion)
VALUES (1, 'FOUND', '黑色雨伞', '图书馆一楼大厅捡到一把黑色长柄雨伞，已放在一楼服务台。', '雨具', '图书馆一楼大厅',
        DATE_SUB(NOW(), INTERVAL 2 HOUR), '黑色', '13800000002',
        '伞柄上有一道白色划痕', '伞柄上有什么特殊痕迹？', '白色划痕', 'PUBLIC', 2, '信息完整，通过'),
       (3, 'FOUND', '白色充电宝', '第三教学楼 302 教室捡到的白色充电宝。', '电子设备', '第三教学楼302教室',
        DATE_SUB(NOW(), INTERVAL 5 HOUR), '白色', '13800000002',
        '背面贴了一张卡通小熊贴纸', '充电宝背面贴了什么？', '卡通贴纸', 'PUBLIC', 2, '信息完整，通过'),
       (5, 'FOUND', '黑色双肩包', '体育馆门口捡到一个黑色双肩包，内有笔记本。', '箱包', '体育馆门口',
        DATE_SUB(NOW(), INTERVAL 10 HOUR), '黑色', '13800000002',
        '左侧口袋有一支蓝色圆珠笔', '包里左侧口袋放了什么？', '蓝色圆珠笔', 'PUBLIC', 1, '信息完整，通过'),
       (7, 'FOUND', '一张学生卡', '第一食堂二楼捡到学生卡一张，姓名张伟。', '证件', '第一食堂二楼',
        DATE_SUB(NOW(), INTERVAL 40 HOUR), NULL, '13800000002',
        '卡套背面写有宿舍号', '卡套背面写了什么？', '宿舍号', 'PUBLIC', 2, '信息完整，通过');

INSERT INTO item (id, type, title, description, category, location, happen_time, color, contact,
                  hidden_feature, verify_question, verify_answer, status, publisher_id, review_opinion)
VALUES (2, 'LOST', '丢失一把黑色雨伞', '在图书馆一楼大厅还书时把一把黑色长柄雨伞落下了。', '雨具', '图书馆一楼大厅',
        DATE_SUB(NOW(), INTERVAL 3 HOUR), '黑色', '13800000001',
        '伞柄有一道白色划痕', NULL, NULL, 'PUBLIC', 1, '信息完整，通过'),
       (4, 'LOST', '丢失一个白色充电宝', '在第三教学楼 302 上完课把白色充电宝忘在座位上了。', '电子设备', '第三教学楼302教室',
        DATE_SUB(NOW(), INTERVAL 6 HOUR), '白色', '13800000001',
        '背面有卡通小熊贴纸', NULL, NULL, 'PENDING', 2, NULL),
       (6, 'LOST', '钥匙串丢失', '钥匙丢了。', '钥匙', '第二食堂',
        DATE_SUB(NOW(), INTERVAL 20 HOUR), '银色', '13800000001',
        '钥匙串上有一个红色小挂件', NULL, NULL, 'REJECTED', 1, '描述过于简略，请补充钥匙数量与挂件特征后重新提交'),
       (8, 'LOST', '学生卡丢了，姓名张伟', '学生卡丢失，姓名张伟，学号后四位 1024。', '证件', '图书馆总服务台',
        DATE_SUB(NOW(), INTERVAL 50 HOUR), NULL, '13800000001',
        '卡套背面写着 3-502', NULL, NULL, 'PUBLIC', 1, '信息完整，通过');

INSERT INTO claim (id, item_id, applicant_id, proof, answer, status, review_opinion, reviewer_id, handover_by, created_at)
VALUES (1, 3, 1, '充电宝外壳贴了小熊贴纸，背面还有一条浅划痕。', '卡通贴纸', 'PENDING', NULL, NULL, NULL,
        DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
       (2, 1, 1, '黑色长柄雨伞，伞柄有白色划痕，是我上周买的。', '白色划痕', 'PENDING', NULL, NULL, NULL,
        DATE_SUB(NOW(), INTERVAL 60 MINUTE)),
       (3, 5, 2, '包内有笔记本电脑，左侧口袋有一支蓝色圆珠笔。', '蓝色圆珠笔', 'APPROVED', '证明与隐藏特征一致，通过', 3, NULL,
        DATE_SUB(NOW(), INTERVAL 300 MINUTE));

INSERT INTO op_log (operator_id, operator_name, action, target_type, target_id, opinion, created_at)
VALUES (3, 'admin', 'CLAIM_REVIEW', 'CLAIM', 3, '通过：证明与隐藏特征一致', DATE_SUB(NOW(), INTERVAL 240 MINUTE)),
       (1, 'student1', 'CLAIM_SUBMIT', 'CLAIM', 1, '申请人提交认领，物品 id=3', DATE_SUB(NOW(), INTERVAL 90 MINUTE)),
       (1, 'student1', 'CLAIM_SUBMIT', 'CLAIM', 2, '申请人提交认领，物品 id=1', DATE_SUB(NOW(), INTERVAL 60 MINUTE));
