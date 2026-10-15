-- ============================================================
-- 第12讲：面部表情实时集成（增量）
-- 保存轻量视觉指标与 Python /finish 返回的 video_summary。
-- ============================================================

CREATE TABLE IF NOT EXISTS `interview_vision_metric` (
  `id`               BIGINT PRIMARY KEY AUTO_INCREMENT,
  `record_id`        BIGINT      NOT NULL COMMENT '面试记录 ID',
  `session_id`       VARCHAR(64) NOT NULL COMMENT 'AI 会话 ID',
  `question_index`   INT         NOT NULL COMMENT '题号',
  `timestamp`        DATETIME    DEFAULT CURRENT_TIMESTAMP COMMENT '采样时间',
  `expression`       VARCHAR(20) COMMENT '当前表情',
  `expression_score` DECIMAL(5,1) COMMENT '表情分',
  `attention_score`  DECIMAL(5,1) COMMENT '注意力分',
  `face_detected`    TINYINT(1)  DEFAULT 0 COMMENT '是否检测到人脸',
  `created_at`       DATETIME    DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_vision_metric_record` (`record_id`, `question_index`),
  KEY `idx_vision_metric_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实时视觉指标采样表';

CREATE TABLE IF NOT EXISTS `interview_vision_summary` (
  `id`                BIGINT PRIMARY KEY AUTO_INCREMENT,
  `record_id`         BIGINT      NOT NULL COMMENT '面试记录 ID',
  `session_id`        VARCHAR(64) NOT NULL COMMENT 'AI 会话 ID',
  `expression_score`  DECIMAL(5,1) COMMENT '表情分',
  `attention_score`   DECIMAL(5,1) COMMENT '注意力分',
  `video_available`   TINYINT(1)  DEFAULT 0 COMMENT '视频分是否可用',
  `face_frames`       INT         COMMENT '有效人脸帧数',
  `duration_seconds`  DECIMAL(8,1) COMMENT '时长',
  `payload_json`      JSON        COMMENT '完整 video_summary',
  `created_at`        DATETIME    DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_vision_summary_record` (`record_id`),
  UNIQUE KEY `uk_vision_summary_session` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视觉分析汇总表';
