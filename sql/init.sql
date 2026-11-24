-- 在已选择的数据库中执行。可重复运行，保留已有账号和业务记录。
-- 管理员由首次启动时的 ADMIN_USERNAME / ADMIN_PASSWORD 创建，学生通过注册页面创建。

CREATE TABLE IF NOT EXISTS sys_user
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

CREATE TABLE IF NOT EXISTS item
(
    id              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    type            VARCHAR(16)   NOT NULL COMMENT '类型：LOST=失物, FOUND=拾物',
    title           VARCHAR(100)  NOT NULL COMMENT '标题',
    description     VARCHAR(1000) NOT NULL COMMENT '描述',
    category        VARCHAR(32)   NOT NULL COMMENT '类别，如 雨具/电子设备/证件',
    location        VARCHAR(100)  NOT NULL COMMENT '丢失或拾取地点',
    happen_time     DATETIME      NOT NULL COMMENT '丢失/拾取时间',
    image_url       VARCHAR(255)  DEFAULT NULL COMMENT '物品照片地址',
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

CREATE TABLE IF NOT EXISTS claim
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

CREATE TABLE IF NOT EXISTS op_log
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

CREATE TABLE IF NOT EXISTS uploaded_image
(
    filename VARCHAR(64) NOT NULL,
    owner_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (filename),
    KEY idx_uploaded_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='真实图片文件及上传者';
