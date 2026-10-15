package com.iflytek.interview.interview.entity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 面试状态机：定义合法的状态转换
 */
public enum InterviewStatus {

    PENDING("pending", "待开始"),
    ONGOING("ongoing", "进行中"),
    COMPLETED("completed", "已完成"),
    CANCELLED("cancelled", "已取消");

    private final String code;
    private final String desc;

    private static final Map<String, List<String>> TRANSITIONS = new HashMap<>();

    static {
        TRANSITIONS.put("pending", Arrays.asList("ongoing", "cancelled"));
        TRANSITIONS.put("ongoing", Arrays.asList("completed", "cancelled"));
        TRANSITIONS.put("completed", Collections.emptyList());
        TRANSITIONS.put("cancelled", Collections.emptyList());
    }

    InterviewStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static boolean canTransition(String from, String to) {
        return TRANSITIONS.getOrDefault(from, Collections.emptyList()).contains(to);
    }
}
