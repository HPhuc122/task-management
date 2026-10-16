package com.taskmanagement.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.taskmanagement.TaskManagementApplication;
import com.taskmanagement.logging.ApiTimingAspect;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

/** Temporary evidence fixture; not compiled into the project artifact. */
public class ChapterEvidence {
    @RestController
    public static class SelfInvocationController {
        public String outer() { return inner(); }
        public String inner() { return "ok"; }
    }

    public static void main(String[] args) throws Exception {
        try (ConfigurableApplicationContext context = SpringApplication.run(TaskManagementApplication.class, args)) {
            Object controller = context.getBean(TaskController.class);
            System.out.println("EVIDENCE TaskController proxy=" + controller.getClass().getName());
            System.out.println("EVIDENCE isCglibProxy=" + AopUtils.isCglibProxy(controller)
                    + " isJdkDynamicProxy=" + AopUtils.isJdkDynamicProxy(controller));
            if (!AopUtils.isCglibProxy(controller)) throw new AssertionError("Expected CGLIB controller proxy");

            Logger logger = (Logger) LoggerFactory.getLogger(ApiTimingAspect.class);
            ListAppender<ILoggingEvent> logs = new ListAppender<>();
            logs.start();
            logger.addAppender(logs);
            try {
                SelfInvocationController fixture = context.getBean(SelfInvocationController.class);
                fixture.outer();
                if (logs.list.size() != 1 || !logs.list.get(0).getFormattedMessage().contains(".outer "))
                    throw new AssertionError("Self-invocation unexpectedly crossed proxy");
                System.out.println("EVIDENCE outer->inner: advice_count=" + logs.list.size() + "; inner_advice_count=0");
                logs.list.clear();
                fixture.inner();
                if (logs.list.size() != 1 || !logs.list.get(0).getFormattedMessage().contains(".inner "))
                    throw new AssertionError("External inner call was not advised");
                System.out.println("EVIDENCE external inner: advice_count=" + logs.list.size());
            } finally {
                logger.detachAppender(logs);
                logs.stop();
            }

            JsonMapper mapper = context.getBean(JsonMapper.class);
            HttpClient http = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:8080";
            var login = HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of(
                            "email", "admin@example.com", "password", System.getenv("DEMO_ADMIN_PASSWORD")))))
                    .build();
            var auth = http.send(login, HttpResponse.BodyHandlers.ofString());
            if (auth.statusCode() != 200) throw new AssertionError("Login failed");
            String token = mapper.readTree(auth.body()).get("accessToken").asText();
            for (String path : new String[]{"/api/categories", "/api/tasks/9223372036854775807"}) {
                var response = http.send(HttpRequest.newBuilder(URI.create(base + path))
                        .header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofString());
                int expected = path.equals("/api/categories") ? 200 : 404;
                if (response.statusCode() != expected) throw new AssertionError("Unexpected status");
                System.out.println("EVIDENCE GET " + path + " -> HTTP " + response.statusCode());
            }
            var unauthenticated = http.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/me")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (unauthenticated.statusCode() != 401) throw new AssertionError("Expected unauthenticated rejection");
            System.out.println("EVIDENCE GET /api/auth/me without token -> HTTP 401 (before controller)");
            var openapi = http.send(HttpRequest.newBuilder(URI.create(base + "/v3/api-docs")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            Files.writeString(Path.of("/workspace/target/chapter-review/openapi.json"), openapi.body());
            System.out.println("EVIDENCE COMPLETE");
        }
    }
}
