-- ==========================================================
-- demo 库增量迁移（幂等）：把后续章节新增的表/列补到「已存在的 demo 库」，
-- 与 schema-demo.sql（仅全新库首次执行）配套。每句都可安全重复执行。
-- 内容：
--   1. resume_delivery —— 候选人简历发送记录表
--   2. interview_record.interview_type —— 模拟/正式面试区分列
-- ==========================================================

CREATE TABLE IF NOT EXISTS `resume_delivery` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `resume_id` BIGINT NOT NULL,
  `version_id` BIGINT NOT NULL,
  `candidate_id` BIGINT NOT NULL,
  `interviewer_id` BIGINT NOT NULL,
  `message` VARCHAR(255),
  `status` VARCHAR(32) NOT NULL DEFAULT 'SENT',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_delivery_candidate_56` (`candidate_id`),
  KEY `idx_delivery_interviewer_57` (`interviewer_id`),
  KEY `idx_delivery_resume_58` (`resume_id`)
);

ALTER TABLE `interview_record`
  ADD COLUMN IF NOT EXISTS `interview_type` VARCHAR(20) NOT NULL DEFAULT 'MOCK';
