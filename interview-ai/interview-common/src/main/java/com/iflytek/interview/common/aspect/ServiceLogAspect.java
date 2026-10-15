package com.iflytek.interview.common.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Aspect
@Component
@Slf4j
public class ServiceLogAspect {
    // 切点：匹配所有 Service 实现类的方法
    @Pointcut("execution(* com.iflytek.interview.*.service.impl..*.*(..))")
    public void serviceMethod() {}

    /**
     * ① @Before 方法执行前：记入参 / 做预检
     * 企业常用：参数预检、权限预检、记录"进来了"
     */
    @Before("serviceMethod()")
    public void beforeInvoke(JoinPoint joinPoint) {
        log.info("【AOP @Before】即将调用: {} 参数: {}",
                joinPoint.getSignature().toShortString(),
                Arrays.toString(joinPoint.getArgs()));
    }

    /**
     * ② @Around 方法前后都能控制：耗时统计（最常用）
     * proceed() 放行；异常必须重新抛出，否则业务层感知不到
     */
    @Around("serviceMethod()")
    public Object aroundInvoke(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            log.info("【AOP @Around】正常返回: {} 耗时={}ms",
                    joinPoint.getSignature().toShortString(),
                    System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            log.error("【AOP @Around】异常: {} msg={}",
                    joinPoint.getSignature().toShortString(), e.getMessage());
            throw e;
        }
    }

    /**
     * ③ @AfterReturning 方法正常返回后：记出参
     * 企业常用：把返回值打日志、对返回结果做统一检查
     */
    @AfterReturning(pointcut = "serviceMethod()", returning = "result")
    public void afterReturn(JoinPoint joinPoint, Object result) {
        log.info("【AOP @AfterReturning】正常返回: {} 结果类型={}",
                joinPoint.getSignature().toShortString(),
                result == null ? "null" : result.getClass().getSimpleName());
    }

    /**
     * ④ @AfterThrowing 方法抛出异常后：统一记异常
     * 企业价值：让"某个方法挂没挂"在日志里可见——ControllerAdvice 只管 HTTP 请求路径，
     *            定时/异步任务（第11讲）里的异常不经过它，要靠这里统一记录
     */
    @AfterThrowing(pointcut = "serviceMethod()", throwing = "e")
    public void afterThrow(JoinPoint joinPoint, Throwable e) {
        log.error("【AOP @AfterThrowing】抛出异常: {} 类型={} msg={}",
                joinPoint.getSignature().toShortString(),
                e.getClass().getSimpleName(), e.getMessage());
    }

    /**
     * ⑤ @After 无论成败都执行：finally 语义收尾
     * 企业常用：清理 ThreadLocal / 释放资源（但注意清理时机要比业务代码晚一步）
     */
    @After("serviceMethod()")
    public void afterInvoke(JoinPoint joinPoint) {
        log.info("【AOP @After】离开方法: {}",
                joinPoint.getSignature().toShortString());
    }
}
