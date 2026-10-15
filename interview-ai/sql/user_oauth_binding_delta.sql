-- ============================================================
-- OAuth 第三方账号绑定表（补缺）
-- 说明：OAuthBinding 实体映射 @TableName("user_oauth_binding")，
--       但既有 6 份脚本均未创建该表，导致 Gitee 登录/绑定报表不存在。
--       字段与 com.iflytek.interview.system.entity.OAuthBinding 对齐。
-- ============================================================

USE `interview_ai`;

CREATE TABLE IF NOT EXISTS `user_oauth_binding` (
    `id`                BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `user_id`           BIGINT       NOT NULL COMMENT '本站用户ID',
    `provider`          VARCHAR(32)  NOT NULL COMMENT '第三方平台标识，如 gitee',
    `provider_user_id`  VARCHAR(64)  NOT NULL COMMENT '第三方平台用户ID',
    `provider_username` VARCHAR(64)  DEFAULT NULL COMMENT '第三方平台用户名',
    `avatar_url`        VARCHAR(255) DEFAULT NULL COMMENT '第三方头像URL',
    `created_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_provider_user` (`provider`, `provider_user_id`),
    INDEX `idx_user_provider` (`user_id`, `provider`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='第三方账号绑定表';
