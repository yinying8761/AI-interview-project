-- ==========================================================
-- 多模态智能模拟面试评测平台 - 第7/8讲 音频/视频采集 增量脚本
-- 适用：在已初始化 interview_ai 的库上追加采集相关表
-- 执行方式： mysql -u root -p < sql/interview_ai_audio_video_delta.sql
-- 说明：使用 IF NOT EXISTS，可重复执行
-- ==========================================================

USE `interview_ai`;

CREATE TABLE IF NOT EXISTS `interview_audio_segment` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL COMMENT '面试会话ID',
  `user_id` BIGINT NOT NULL COMMENT '采集人',
  `question_index` INT NOT NULL DEFAULT 1 COMMENT '题目顺序',
  `segment_seq` INT NOT NULL DEFAULT 1 COMMENT '同一题目内的片段序号',
  `start_at` DATETIME NOT NULL COMMENT '片段开始时间',
  `end_at` DATETIME DEFAULT NULL COMMENT '片段结束时间',
  `duration_ms` INT DEFAULT 0 COMMENT '片段时长毫秒',
  `file_url` VARCHAR(512) DEFAULT NULL COMMENT '音频文件地址',
  `mime_type` VARCHAR(64) DEFAULT NULL COMMENT '音频格式',
  `file_size` BIGINT DEFAULT 0 COMMENT '文件大小',
  `sample_rate` INT DEFAULT NULL COMMENT '采样率',
  `channels` INT DEFAULT 1 COMMENT '声道数',
  `avg_volume` DECIMAL(6,4) DEFAULT NULL COMMENT '平均音量RMS',
  `max_volume` DECIMAL(6,4) DEFAULT NULL COMMENT '最大音量RMS',
  `silence_ratio` DECIMAL(6,4) DEFAULT NULL COMMENT '静音比例',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RECORDING' COMMENT 'RECORDING/UPLOADED/ANALYZED/FAILED',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_record_question_segment` (`record_id`, `question_index`, `segment_seq`),
  KEY `idx_audio_record_question` (`record_id`, `question_index`),
  KEY `idx_audio_status` (`status`),
  KEY `idx_audio_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试音频片段表';

CREATE TABLE IF NOT EXISTS `interview_audio_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL,
  `segment_id` BIGINT DEFAULT NULL,
  `question_index` INT NOT NULL DEFAULT 1,
  `event_type` VARCHAR(64) NOT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `payload_json` JSON DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_audio_event_record_question` (`record_id`, `question_index`),
  KEY `idx_audio_event_segment` (`segment_id`),
  KEY `idx_audio_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='音频采集事件表';

CREATE TABLE IF NOT EXISTS `interview_video_frame` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL COMMENT '面试会话ID',
  `user_id` BIGINT NOT NULL COMMENT '采集人',
  `question_index` INT NOT NULL DEFAULT 1 COMMENT '题目顺序',
  `frame_seq` BIGINT NOT NULL COMMENT '帧序号',
  `key_frame` TINYINT DEFAULT 0 COMMENT '0-普通帧 1-关键帧',
  `timestamp_ms` BIGINT NOT NULL COMMENT '相对会话开始毫秒数',
  `captured_at` DATETIME NOT NULL COMMENT '浏览器采集时间',
  `width` INT NOT NULL COMMENT '宽度',
  `height` INT NOT NULL COMMENT '高度',
  `brightness` DECIMAL(8,4) DEFAULT NULL COMMENT '亮度均值',
  `sharpness` DECIMAL(10,4) DEFAULT NULL COMMENT '清晰度估计',
  `diff_ratio` DECIMAL(8,4) DEFAULT NULL COMMENT '与上一帧差异率',
  `file_url` VARCHAR(512) DEFAULT NULL COMMENT '关键帧文件地址',
  `thumbnail_url` VARCHAR(512) DEFAULT NULL COMMENT '缩略图地址',
  `mime_type` VARCHAR(64) DEFAULT 'image/jpeg' COMMENT '图像格式',
  `file_size` BIGINT DEFAULT 0 COMMENT '文件大小',
  `status` VARCHAR(32) DEFAULT 'CREATED' COMMENT 'CREATED/UPLOADED/FAILED',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_record_question_frame` (`record_id`, `question_index`, `frame_seq`),
  KEY `idx_video_frame_record_question` (`record_id`, `question_index`),
  KEY `idx_video_frame_key` (`record_id`, `question_index`, `key_frame`),
  KEY `idx_video_frame_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试视频帧表';

CREATE TABLE IF NOT EXISTS `interview_video_segment` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL COMMENT '面试会话ID',
  `user_id` BIGINT NOT NULL COMMENT '采集人',
  `question_index` INT NOT NULL DEFAULT 1 COMMENT '题目顺序',
  `segment_seq` INT NOT NULL DEFAULT 1 COMMENT '同一题目内的片段序号',
  `start_at` DATETIME NOT NULL COMMENT '开始时间',
  `end_at` DATETIME DEFAULT NULL COMMENT '结束时间',
  `duration_ms` INT DEFAULT 0 COMMENT '片段时长',
  `file_url` VARCHAR(512) DEFAULT NULL COMMENT '视频文件地址',
  `thumbnail_url` VARCHAR(512) DEFAULT NULL COMMENT '封面图地址',
  `mime_type` VARCHAR(64) DEFAULT NULL COMMENT '视频格式',
  `file_size` BIGINT DEFAULT 0 COMMENT '文件大小',
  `width` INT DEFAULT NULL COMMENT '视频宽度',
  `height` INT DEFAULT NULL COMMENT '视频高度',
  `frame_rate` INT DEFAULT NULL COMMENT '帧率',
  `bitrate` INT DEFAULT NULL COMMENT '码率',
  `network_level` VARCHAR(32) DEFAULT NULL COMMENT 'GOOD/NORMAL/POOR',
  `status` VARCHAR(32) NOT NULL DEFAULT 'RECORDING' COMMENT 'RECORDING/UPLOADED/FAILED',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY `uk_video_record_question_segment` (`record_id`, `question_index`, `segment_seq`),
  KEY `idx_video_segment_record_question` (`record_id`, `question_index`),
  KEY `idx_video_segment_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='面试视频片段表';

CREATE TABLE IF NOT EXISTS `interview_video_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `record_id` BIGINT NOT NULL,
  `segment_id` BIGINT DEFAULT NULL,
  `frame_id` BIGINT DEFAULT NULL,
  `question_index` INT NOT NULL DEFAULT 1,
  `event_type` VARCHAR(64) NOT NULL,
  `operator_id` BIGINT DEFAULT NULL,
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `payload_json` JSON DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_video_event_record_question` (`record_id`, `question_index`),
  KEY `idx_video_event_segment` (`segment_id`),
  KEY `idx_video_event_frame` (`frame_id`),
  KEY `idx_video_event_type` (`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='视频采集事件表';

