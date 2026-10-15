package com.iflytek.interview.interview.vo;

import com.iflytek.interview.interview.entity.QuestionPhase;
import lombok.Data;

/**
 * 面试状态快照。
 * 在刷新 / 断线重连时返回给前端，让前端据此恢复界面与采集逻辑。
 * 这个对象是"只读"的聚合视图，不承担状态写入。
 */
@Data
public class InterviewStateSnapshot {

    /** 面试记录 ID，用于定位一场面试。 */
    private Long recordId;

    /** 业务状态：pending / ongoing / completed / cancelled。 */
    private String status;

    /** 当前题号，与音频片段、视频帧的 questionIndex 对齐。 */
    private Integer questionIndex;

    /** 当前题目阶段：ASKING / LISTENING / SUBMITTED / TIMEOUT。 */
    private QuestionPhase phase;

    /** Python AI 会话映射键。 */
    private String aiSessionId;

    /** AI 会话已问题目数（可选，用于进度展示）。 */
    private Integer aiQuestionCount;

    /** 是否处于 AI 检查点（已问满，等待继续或结束）。 */
    private Boolean aiCheckpoint;

    /** 音频通道状态：IDLE / TTS_PLAYING / RECORDING / STOPPED。 */
    private String audioState;

    /** 视频通道状态：IDLE / CAPTURING / STOPPED。 */
    private String videoState;

    /** 最近一次失败原因，便于前端提示重试。 */
    private String lastFailReason;
}
