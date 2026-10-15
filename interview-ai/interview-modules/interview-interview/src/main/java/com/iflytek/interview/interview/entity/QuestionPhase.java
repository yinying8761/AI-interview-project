package com.iflytek.interview.interview.entity;

/**
 * 当前题目阶段枚举。
 * 描述一场 AI 面试中"当前题目"的推进进度，前端与采集模块据此决定
 * 是展示题目、开始录音还是提交回答。
 */
public enum QuestionPhase {

    /** AI 出题完成，正在 TTS 播放或等待候选人准备。 */
    ASKING("ASKING", "出题中"),

    /** 候选人在回答，音频与视频采集进行中，并准备语音转写。 */
    LISTENING("LISTENING", "回答中"),

    /** 回答文本已保存并提交 AI，等待生成下一题或进入检查点。 */
    SUBMITTED("SUBMITTED", "已提交"),

    /** 超过作答时限，自动结束当前题。 */
    TIMEOUT("TIMEOUT", "超时");

    private final String code;
    private final String desc;

    QuestionPhase(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
