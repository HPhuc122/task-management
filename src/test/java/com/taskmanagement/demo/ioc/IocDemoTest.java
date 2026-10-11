package com.taskmanagement.demo.ioc;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IocDemoTest {
    @Test
    void springCreatesAndInjectsOneManagedGreeter() {
        CasualGreeter managed;
        CasualGreeter manual = new CasualGreeter();
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CasualGreeter.class, Formalgreeter.class,
                    GreetingService.class, ReminderService.class);
            context.refresh();

            managed = context.getBean(CasualGreeter.class);
            assertSame(managed, context.getBean(GreetingService.class).exposeGreeter());
            assertSame(managed, context.getBean(ReminderService.class).exposeGreeter());
            assertTrue(managed.isInitializedBySpring());
            assertFalse(managed.isDestroyedBySpring());
            assertFalse(manual.isInitializedBySpring());
        }
        assertTrue(managed.isDestroyedBySpring());
        assertFalse(manual.isDestroyedBySpring());
    }
}
