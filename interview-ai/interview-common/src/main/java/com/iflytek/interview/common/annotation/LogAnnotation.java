package com.iflytek.interview.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解：标注在需要审计的接口/方法上
 */
@Target(ElementType.METHOD)       // 只能标在方法上
@Retention(RetentionPolicy.RUNTIME)  // 运行时保留（AOP 才能在运行时读到）
public @interface LogAnnotation {

    String module() default "";      // 模块名，如 "用户管理"
    String operation() default "";   // 操作描述，如 "新增用户"
}