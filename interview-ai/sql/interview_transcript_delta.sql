-- ============================================================
-- 第11讲：语音转文字实时集成（增量）
-- 存储 Python /api/ai/asr 返回的转写结果与分段。
-- ============================================================

CREATE TABLE IF NOT EXISTS `interview_transcript` (
  `id`             BIGINT       PRIMARY KEY AUTO_INCREMENT,
  `record_id`      BIGINT       NOT NULL COMMENT '面试记录 ID',
  `question_index` INT          NOT NULL COMMENT '题号',
  `segment_id`     BIGINT       NOT NULL COMMENT '音频片段 ID',
  `text`           TEXT         COMMENT '完整转写文本',
  `language`       VARCHAR(20)  COMMENT '语言',
  `segments_json`  JSON         COMMENT '转写分段(含起止时间)',
  `status`         VARCHAR(20)  DEFAULT 'PARSED' COMMENT '状态',
  `created_at`     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_record_q` (`record_id`, `question_index`)
);

CREATE TABLE IF NOT EXISTS `interview_transcript_segment` (
  `id`            BIGINT   PRIMARY KEY AUTO_INCREMENT,
  `transcript_id` BIGINT   NOT NULL COMMENT '所属转写结果 ID',
  `seq`           INT      NOT NULL COMMENT '分段序号',
  `start_ms`      BIGINT   NOT NULL COMMENT '开始(毫秒)',
  `end_ms`        BIGINT   NOT NULL COMMENT '结束(毫秒)',
  `text`          VARCHAR(500) COMMENT '分段文本',
  `confidence`    DECIMAL(5,4) COMMENT '置信度',
  KEY `idx_transcript` (`transcript_id`)
);
