package com.cpallas.expenses.observability;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Logs user-facing bot operations without serializing method arguments or results.
 * Expense descriptions, amounts, Telegram updates and identifiers stay out of logs.
 */
@Aspect
@Component
@Slf4j
public class UserLogicLoggingAspect {

    @Around("execution(public * com.cpallas.expenses.controller.consumer..*(..))"
            + " || execution(public * com.cpallas.expenses.controller.handler..*(..))"
            + " || execution(public * com.cpallas.expenses.controller.process..*(..))"
            + " || execution(public * com.cpallas.expenses.controller.MlTrainingController.*(..))"
            + " || execution(public * com.cpallas.expenses.service..*(..))")
    public Object logUserOperation(ProceedingJoinPoint joinPoint) throws Throwable {
        String operation = operationName(joinPoint);
        long startedAt = System.nanoTime();
        log.info("User operation started: operation={}", operation);

        try {
            Object result = joinPoint.proceed();
            log.info(
                    "User operation completed: operation={} durationMs={}",
                    operation,
                    elapsedMillis(startedAt)
            );
            return result;
        } catch (Throwable exception) {
            log.error(
                    "User operation failed: operation={} durationMs={} errorType={}",
                    operation,
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName(),
                    exception
            );
            throw exception;
        }
    }

    private String operationName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return "%s.%s".formatted(
                signature.getDeclaringType().getSimpleName(),
                signature.getMethod().getName()
        );
    }

    private long elapsedMillis(long startedAt) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
    }
}
