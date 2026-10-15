-- ============================================================
-- 第09讲：面试过程状态与 AI 会话一致性（增量）
-- 为面试记录补充状态与 AI 会话映射字段。
-- 线下库建议先备份，再执行本脚本。
-- ============================================================

ALTER TABLE `interview_record`
  ADD COLUMN `ai_session_id` VARCHAR(64) NULL COMMENT 'Python AI 会话映射键',
  ADD COLUMN `current_phase` VARCHAR(20) NULL DEFAULT 'ASKING' COMMENT '当前题目阶段',
  ADD COLUMN `submit_id` VARCHAR(64) NULL COMMENT '最近一次提交的幂等键';

-- 校验：确认字段已添加
-- SHOW COLUMNS FROM interview_record LIKE 'current_phase';
