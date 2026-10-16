-- ==========================================================
-- 增量脚本：候选人简历「发送给面试官」功能
-- 内容：新增简历发送记录表 resume_delivery
-- 执行方式：对已存在的 interview_ai 库执行本脚本
--   mysql -u root -p interview_ai < sql/interview_ai_resume_delivery_delta.sql
-- ==========================================================

CREATE TABLE IF NOT EXISTS `resume_delivery` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '发送ID',
  `resume_id` BIGINT NOT NULL COMMENT '简历ID',
  `version_id` BIGINT NOT NULL COMMENT '发送的简历版本ID',
  `candidate_id` BIGINT NOT NULL COMMENT '候选人(发送者)用户ID',
  `interviewer_id` BIGINT NOT NULL COMMENT '面试官(接收者)用户ID',
  `message` VARCHAR(255) COMMENT '留言备注',
  `status` VARCHAR(32) NOT NULL DEFAULT 'SENT' COMMENT 'SENT/READ',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY `idx_delivery_candidate` (`candidate_id`),
  KEY `idx_delivery_interviewer` (`interviewer_id`),
  KEY `idx_delivery_resume` (`resume_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历发送记录表';
