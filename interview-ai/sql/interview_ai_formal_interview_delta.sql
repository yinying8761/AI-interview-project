-- ==========================================================
-- 增量脚本：区分「模拟面试」与「正式面试」+ 面试官发布正式面试
-- 内容：interview_record 新增 interview_type 列（MOCK/FORMAL）
-- 执行方式：对已存在的 interview_ai 库执行本脚本
--   mysql -u root -p interview_ai < sql/interview_ai_formal_interview_delta.sql
-- 说明：旧数据（候选人自练的模拟面试）会自动回填默认值 'MOCK'。
-- ==========================================================

ALTER TABLE `interview_record`
  ADD COLUMN `interview_type` VARCHAR(20) NOT NULL DEFAULT 'MOCK'
  COMMENT 'MOCK-模拟面试 FORMAL-正式面试';
