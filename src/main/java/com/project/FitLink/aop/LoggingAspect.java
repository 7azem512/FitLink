package com.project.FitLink.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Slf4j
@Aspect
@Component
public class LoggingAspect {

    @Pointcut("within(com.project.FitLink.service..*) || within(com.project.FitLink.controller..*)")
    public void applicationLayer() {}

    @Around("applicationLayer()")
    public Object logAround(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("[{}::{}] called with args: {}", className, methodName, maskSensitiveArgs(methodName, args));

        long start = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;
            log.info("[{}::{}] completed in {}ms", className, methodName, duration);
            return result;
        } catch (Throwable ex) {
            long duration = System.currentTimeMillis() - start;
            log.error("[{}::{}] failed after {}ms with: {}", className, methodName, duration, ex.getMessage());
            throw ex;
        }
    }

    private Object[] maskSensitiveArgs(String methodName, Object[] args) {
        if (args == null || args.length == 0) return args;
        String method = methodName.toLowerCase();
        if (method.contains("password") || method.contains("login") || method.contains("register")) {
            return Arrays.stream(args)
                    .map(arg -> arg != null ? "***" : null)
                    .toArray();
        }
        return args;
    }
}
