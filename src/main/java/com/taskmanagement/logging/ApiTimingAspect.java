package com.taskmanagement.logging;

import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class ApiTimingAspect {
    private static final Logger log = LoggerFactory.getLogger(ApiTimingAspect.class);

    @Around("@within(org.springframework.web.bind.annotation.RestController) "
            + "&& execution(public * com.taskmanagement.controller..*(..))")
    public Object logControllerTime(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.nanoTime();
        boolean succeeded = false;
        try {
            Object result = joinPoint.proceed();
            succeeded = true;
            return result;
        } finally {
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            log.info("API {}.{} completed in {} ms ({})",
                    joinPoint.getSignature().getDeclaringType().getSimpleName(),
                    joinPoint.getSignature().getName(), elapsedMillis,
                    succeeded ? "success" : "failure");
        }
    }
}
