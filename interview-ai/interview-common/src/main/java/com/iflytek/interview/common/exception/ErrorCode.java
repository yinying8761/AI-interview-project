package com.iflytek.interview.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // ===== 通用（与 HTTP 语义对齐） =====
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未登录"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统异常"),

    // ===== 用户模块（1000+） =====
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_EXISTS(1002, "用户已存在"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),
    USER_DISABLED(1004, "账号已被禁用"),

    // ===== 面试模块（2000+） =====
    SCENARIO_NOT_FOUND(2001, "面试场景不存在"),

    // ===== 记录模块（3000+） =====
    RECORD_NOT_FOUND(3001, "面试记录不存在"),
    RECORD_ALREADY_STARTED(3002, "面试已经开始"),
    RECORD_ALREADY_COMPLETED(3003, "面试已结束"),
    ADMIN_CANNOT_INTERVIEW(3004, "管理员无需参加面试"),
    INTERVIEW_ONLY_CANDIDATE(3005, "仅候选人可以参加面试"),

    // ===== 文件模块（4000+） =====
    FILE_UPLOAD_FAILED(4001, "文件上传失败"),
    FILE_DELETE_FAILED(4002, "文件删除失败"),
    FILE_URL_FAILED(4003, "获取文件地址失败"),

    // ===== 题目模块（5000+） =====
    QUESTION_NOT_FOUND(5001, "题目不存在"),
    QUESTION_POOL_INSUFFICIENT(5002, "可用题目数量不足");

    private final Integer code;
    private final String message;
}
