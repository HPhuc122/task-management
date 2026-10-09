package com.taskmanagement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.taskmanagement.logging.ApiTimingAspect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.web.bind.annotation.RestController;

class ApiTimingAspectTest {
    private final Logger logger = (Logger) LoggerFactory.getLogger(ApiTimingAspect.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void stopCapturingLogs() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void logsSuccessfulControllerExecutionWithoutArguments() {
        SampleController controller = proxy();

        assertEquals("ok", controller.succeed("private request body"));

        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.matches("API SampleController.succeed completed in \\d+ ms \\(success\\)"));
        assertFalse(message.contains("private request body"));
    }

    @Test
    void logsFailedControllerExecutionAndPropagatesException() {
        SampleController controller = proxy();

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> controller.fail("private request body"));

        assertEquals("private failure detail", exception.getMessage());
        assertEquals(1, appender.list.size());
        String message = appender.list.get(0).getFormattedMessage();
        assertTrue(message.matches("API SampleController.fail completed in \\d+ ms \\(failure\\)"));
        assertFalse(message.contains("private request body"));
        assertFalse(message.contains("private failure detail"));
    }

    private SampleController proxy() {
        AspectJProxyFactory factory = new AspectJProxyFactory(new SampleController());
        factory.addAspect(new ApiTimingAspect());
        return factory.getProxy();
    }

    @RestController
    public static class SampleController {
        public String succeed(String body) {
            return "ok";
        }

        public String fail(String body) {
            throw new IllegalStateException("private failure detail");
        }
    }
}
